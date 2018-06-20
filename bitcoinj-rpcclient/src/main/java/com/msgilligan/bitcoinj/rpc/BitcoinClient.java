package com.msgilligan.bitcoinj.rpc;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.msgilligan.bitcoinj.json.conversion.HexUtil;
import com.msgilligan.bitcoinj.json.pojo.*;
import com.msgilligan.bitcoinj.json.conversion.RpcClientModule;
import org.consensusj.jsonrpc.JsonRPCException;
import org.consensusj.jsonrpc.JsonRPCStatusException;
import org.consensusj.jsonrpc.RPCClient;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.RegTestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * = JSON-RPC Client for *Bitcoin Core*
 *
 * A strongly-typed wrapper for a {@index Bitcoin} RPC client using the
 * https://bitcoin.org/en/developer-reference#bitcoin-core-apis[Bitcoin Core JSON-RPC API].
 * https://bitcoinj.github.io[bitcoinj] types are used where appropriate.
 * For example, requesting a block hash will return a {@link org.bitcoinj.core.Sha256Hash}:
 *
 * [source,java]
 * --
 * Sha256Hash hash = client.getBlockHash(342650);
 * --
 *
 * Requesting a Bitcoin balance will return the amount as a {@link org.bitcoinj.core.Coin}:
 * [source,java]
 * --
 * Coin balance = client.getBalance();
 * --
 *
 * This version is written to be compatible with Bitcoin Core 0.10.4 and later. If used with
 * Omni Core (an enhanced Bitcoin Core with Omni Protocol support) Omni Core 0.0.11.1
 * or later is required.
 *
 * NOTE: This is still a work-in-progress and the API will change.
 *
 */
public class BitcoinClient extends RPCClient implements NetworkParametersProperty {
    private static final Logger log = LoggerFactory.getLogger(BitcoinClient.class);

    private static final int SECOND_IN_MSEC = 1000;
    private static final int RETRY_SECONDS = 1;
    private static final int MESSAGE_SECONDS = 10;

    private int serverVersion = 0;    // 0 means unknown serverVersion

    protected final Context context;

    /**
     * Construct a BitcoinClient from URI, user name, and password.
     * @param server URI of the Bitcoin RPC server
     * @param rpcuser Username (if required)
     * @param rpcpassword Password (if required)
     * @deprecated You need to specify NetworkParameters, this constructor defaults to RegTest
     * @see BitcoinClient#BitcoinClient(NetworkParameters, URI, String, String)
     */
    @Deprecated
    public BitcoinClient(URI server, String rpcuser, String rpcpassword) {
        this(RegTestParams.get(), server, rpcuser, rpcpassword);
    }

    /**
     * Construct a BitcoinClient from Network Parameters, URI, user name, and password.
     * @param netParams Correct Network Parameters for destination server
     * @param server URI of the Bitcoin RPC server
     * @param rpcuser Username (if required)
     * @param rpcpassword Password (if required)
     */
    public BitcoinClient(NetworkParameters netParams, URI server, String rpcuser, String rpcpassword) {
        super(server, rpcuser, rpcpassword);
        this.context = new Context(netParams);
        mapper.registerModule(new RpcClientModule(context.getParams()));
    }

    /**
     * Construct a BitcoinClient from an RPCConfig data object.
     * @param config Contains URI, user name, and password
     */
    public BitcoinClient(RPCConfig config) {
        this(config.getNetParams(), config.getURI(), config.getUsername(), config.getPassword());
    }

    /**
     * Get network parameters
     * @return network parameters for the server
     */
    @Override
    public NetworkParameters getNetParams() {
        return context.getParams();
    }

    /**
     * Get a (cached after first call) serverVersion number
     * @return serverVersion number of bitcoin node
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    private int getServerVersion() throws IOException, JsonRPCStatusException {
        if (serverVersion == 0) {
            serverVersion = getNetworkInfo().getVersion();
        }
        return serverVersion;
    }

    /**
     * Wait until the server is available.
     *
     * Keep trying, ignoring (and logging) a known list of exception conditions that may occur while waiting for
     * a `bitcoind` server to start up. This is similar to the behavior enabled by the `-rpcwait`
     * option to the `bitcoin-cli` command-line tool.
     *
     * @param timeout Timeout in seconds
     * @return true if ready, false if timeout or interrupted
     */
    public Boolean waitForServer(int timeout) throws JsonRPCException {

        log.debug("Waiting for server RPC ready...");

        String status;          // Status message for logging
        String statusLast = null;
        int seconds = 0;
        while (seconds < timeout) {
            try {
                Integer block = this.getBlockCount();
                if (block != null) {
                    log.debug("RPC Ready.");
                    return true;
                }
                status = "getBlock returned null";
            } catch (SocketException se) {
                // These are expected exceptions while waiting for a server
                if (se.getMessage().equals("Unexpected end of file from server") ||
                        se.getMessage().equals("Connection reset") ||
                        se.getMessage().equals("Connection refused") ||
                        se.getMessage().equals("recvfrom failed: ECONNRESET (Connection reset by peer)")) {
                    status = se.getMessage();
                } else {
                    throw new JsonRPCException("Unexpected exception in waitForServer", se) ;
                }

            } catch (EOFException ignored) {
                /* Android exception, ignore */
                // Expected exceptions on Android, RoboVM
                status = ignored.getMessage();
            } catch (JsonRPCStatusException e) {
                // If server is in "warm-up" mode, e.g. validating/parsing the blockchain...
                if (e.jsonRPCCode == -28) {
                    // ...then grab text message for status logging
                    status = e.getMessage();
                } else {
                    throw e;
                }
            } catch (IOException e) {
                // Ignore all IOExceptions
                status = e.getMessage();
            }
            try {
                // Log status messages only once, if new or updated
                if (!status.equals(statusLast)) {
                    log.info("RPC Status: " + status);
                    statusLast = status;
                }
                Thread.sleep(RETRY_SECONDS * SECOND_IN_MSEC);
                seconds += RETRY_SECONDS;
            } catch (InterruptedException e) {
                log.error(e.toString());
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("waitForServer() timed out after {} seconds.", timeout);
        return false;
    }

    /**
     * Wait for RPC server to reach specified block height.
     *
     * @param blockHeight Block height to wait for
     * @param timeout     Timeout in seconds
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     * @return True if blockHeight reached, false if timeout or interrupted
     */
    public Boolean waitForBlock(int blockHeight, int timeout) throws JsonRPCStatusException, IOException {

        log.info("Waiting for server to reach block " + blockHeight);

        int seconds = 0;
        while (seconds < timeout) {
            Integer block = this.getBlockCount();
            if (block >= blockHeight) {
                log.info("Server is at block " + block + " returning 'true'.");
                return true;
            } else {
                try {
                    if (seconds % MESSAGE_SECONDS == 0) {
                        log.debug("Server at block " + block);
                    }
                    Thread.sleep(RETRY_SECONDS * SECOND_IN_MSEC);
                    seconds += RETRY_SECONDS;
                } catch (InterruptedException e) {
                    log.error(e.toString());
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        log.error("Timeout waiting for block " + blockHeight);
        return false;
    }

    /**
     * Returns the number of blocks in the longest block chain.
     *
     * @return The current block count
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Integer getBlockCount() throws JsonRPCStatusException, IOException {
        return send("getblockcount");
    }

    /**
     * Returns the hash of block in best-block-chain at index provided.
     *
     * @param index The block index
     * @return The block hash
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Sha256Hash getBlockHash(Integer index) throws JsonRPCStatusException, IOException {
        return send("getblockhash", Sha256Hash.class, index);
    }

    /**
     * Returns information about a block with the given block hash.
     *
     * @param hash The block hash
     * @return The information about the block
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public BlockInfo getBlockInfo(Sha256Hash hash) throws JsonRPCStatusException, IOException {
        // Use "verbose = true"
        return send("getblock", BlockInfo.class, hash, true);
    }

    public Block getBlock(Sha256Hash hash) throws JsonRPCStatusException, IOException {
        // Use "verbose = false"
        return send("getblock", Block.class, hash, false);
    }

    /**
     * Returns information about a block at index provided.
     *
     * @param index The block index
     * @return The information about the block
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Block getBlock(Integer index) throws JsonRPCStatusException, IOException {
        Sha256Hash blockHash = getBlockHash(index);
        return getBlock(blockHash);
    }

    /**
     * Turn generation on/off
     *
     * Note: `setgenerate` has been removed from regtest mode in recent Bitcoin Core, as `generate`
     * should be used instead)
     *
     * @param generate        turn generation on or off
     * @param genproclimit    Generation is limited to [genproclimit] processors, -1 is unlimited
     * @return List<Sha256Hash>  list containing  block header hashes of the generated blocks or empty list
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     *
     */
    public List<Sha256Hash> setGenerate(Boolean generate, Long genproclimit) throws JsonRPCStatusException, IOException {
        JavaType resultType = mapper.getTypeFactory().constructCollectionType(List.class, Sha256Hash.class);
        return send("setgenerate", resultType, generate, genproclimit);
    }


    /**
     * generate blocks (RegTest mode only)
     * @since Bitcoin Core 0.11.0
     *
     * @param numBlocks number of blocks to generate
     * @return list containing block header hashes of the generated blocks
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<Sha256Hash> generate(int numBlocks) throws JsonRPCStatusException, IOException {
        if (getServerVersion() > 110000) {
            JavaType resultType = mapper.getTypeFactory().constructCollectionType(List.class, Sha256Hash.class);
            return send("generate", resultType, numBlocks);
        } else {
            // For backward compatibility, to be removed eventually
            return setGenerate(true, (long) numBlocks);
        }
    }

    /**
     * Convenience method for generating a single block when in RegTest mode
     * @see BitcoinClient#generate(int numBlocks)
     */
    public List<Sha256Hash> generate() throws IOException, JsonRPCStatusException {
        return generate(1);
    }

    /**
     * Convenience method for generating a single block when in RegTest mode
     * @deprecated Use BitcoinClient#generate()
     * @see BitcoinClient#generate()
     */
// the Deprecated annotation was causing Groovy in OmniJ to do
// something weird (like not find the method),
// I can't remember exactly what it was -- try adding it back as part
// of testing for release 0.2.2
    @Deprecated
    public List<Sha256Hash> generateBlock() throws JsonRPCStatusException, IOException {
        return generate();
    }

    /**
     * Convenience method for generating blocks when in RegTest mode
     *
     * @param blocks number of blocks to generate
     * @deprecated Use BitcoinClient#generate(int)
     * @see BitcoinClient#generate(int)
     */
    @Deprecated
    public List<Sha256Hash> generateBlocks(Long blocks) throws JsonRPCStatusException, IOException {
        return generate(blocks.intValue());
    }

    /**
     * Creates a new Bitcoin address for receiving payments, linked to the default account "".
     *
     * @return A new Bitcoin address
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Address getNewAddress() throws JsonRPCStatusException, IOException {
        return getNewAddress(null);
    }

    /**
     * Creates a new Bitcoin address for receiving payments.
     *
     * @param account The account name for the address to be linked to.
     * @return A new Bitcoin address
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Address getNewAddress(String account) throws JsonRPCStatusException, IOException {
        return send("getnewaddress", Address.class, account);
    }

    /**
     * Returns the Bitcoin address linked to the given account.
     *
     * @param account The account name linked to the address.
     * @return The Bitcoin address
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Address getAccountAddress(String account) throws JsonRPCStatusException, IOException {
        return send("getaccountaddress", Address.class, account);
    }

    /**
     * Return the private key from the server.
     *
     * Note: must be in wallet mode with unlocked or unencrypted wallet.
     *
     * @param address Address corresponding to the private key to return
     * @return The private key
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public ECKey dumpPrivKey(Address address) throws JsonRPCStatusException, IOException {
        return send("dumpprivkey", ECKey.class, address);
    }

    /**
     * Move a specified amount from one account in your wallet to another.
     *
     * @param fromaccount The name of the account to move funds from, which may be the default account using ""
     * @param toaccount   The name of the account to move funds to, which may be the default account using ""
     * @param amount      The amount to move
     * @param minconf     Only use funds with at least this many confirmations
     * @param comment     An optional comment, stored in the wallet only
     * @return True, if successful, and false otherwise
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Boolean moveFunds(Address fromaccount, Address toaccount, Coin amount, Integer minconf, String comment)
            throws JsonRPCStatusException,
            IOException {
        return send("move", fromaccount, toaccount, amount, minconf, comment);
    }

    /**
     * Creates a raw transaction spending the given inputs to the given destinations.
     *
     * Note: the transaction inputs are not signed, and the transaction is not stored in the wallet or transmitted to
     * the network.
     *
     * @param inputs  The outpoints to spent
     * @param outputs The destinations and amounts to transfer
     * @return The hex-encoded raw transaction
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public String createRawTransaction(List<Outpoint> inputs, Map<Address, Coin> outputs)
            throws JsonRPCStatusException, IOException {
        return send("createrawtransaction", inputs, outputs);
    }

    /**
     * Signs inputs of a raw transaction.
     *
     * @param unsignedTransaction The hex-encoded raw transaction
     * @return The signed transaction and information whether it has a complete set of signature
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public SignedRawTransaction signRawTransaction(String unsignedTransaction) throws JsonRPCStatusException, IOException {
        return send("signrawtransaction", SignedRawTransaction.class, unsignedTransaction);
    }

    /**
     * Get raw transaction info as hex->bitcoinj or verbose (json->POJO)
     * @param txid Transaction ID/hash
     * @param verbose `true` to return JSON transaction
     * @return  RawTransactionInfo if verbose, otherwise Transaction
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     * @deprecated Use getRawTransaction or getRawTransactionInfo as appropriate
     */
    @Deprecated
    public Object getRawTransaction(Sha256Hash txid, Boolean verbose) throws JsonRPCStatusException, IOException {
        Object result;
        if (verbose) {
            result = getRawTransactionInfo(txid);    // Verbose means JSON
        } else {
            result = getRawTransaction(txid);  // Not-verbose is bitcoinj Transaction
        }
        return result;
    }

    /**
     * Get a "raw" transaction (which we map to a bitcoinj transaction)
     * @param txid Transaction ID/hash
     * @return bitcoinj Transaction
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Transaction getRawTransaction(Sha256Hash txid) throws JsonRPCStatusException, IOException {
        String hexEncoded = send("getrawtransaction", txid);
        byte[] raw = HexUtil.hexStringToByteArray(hexEncoded);
        return new Transaction(context.getParams(), raw);
    }

    /**
     * Get a "raw" transaction as JSON (which we map to a RawTransactionInfo POJO)
     * @param txid Transaction ID/hash
     * @return RawTransactionInfo POJO
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public RawTransactionInfo getRawTransactionInfo(Sha256Hash txid) throws JsonRPCStatusException, IOException {
        return send("getrawtransaction", RawTransactionInfo.class, txid, 1);
    }

    public Sha256Hash sendRawTransaction(Transaction tx) throws JsonRPCStatusException, IOException {
        return sendRawTransaction(tx, null);
    }

    public Sha256Hash sendRawTransaction(String hexTx) throws JsonRPCStatusException, IOException {
        return sendRawTransaction(hexTx, null);
    }

    public Sha256Hash sendRawTransaction(Transaction tx, Boolean allowHighFees) throws JsonRPCStatusException, IOException {
        return send("sendrawtransaction", Sha256Hash.class, tx, allowHighFees);
    }

    public Sha256Hash sendRawTransaction(String hexTx, Boolean allowHighFees) throws JsonRPCStatusException, IOException {
        return send("sendrawtransaction", Sha256Hash.class, hexTx, allowHighFees);
    }

    public Coin getReceivedByAddress(Address address) throws JsonRPCStatusException, IOException {
        return getReceivedByAddress(address, 1);   // Default to 1 or more confirmations
    }

    /**
     * get total amount received by an address.
     *
     * @param address Address to query
     * @param minConf minimum number of confirmations
     * @return Is now returning `Coin`, if you need to convert use `BitcoinMath.btcToCoin(result)`
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Coin getReceivedByAddress(Address address, Integer minConf) throws JsonRPCStatusException, IOException {
        return send("getreceivedbyaddress", Coin.class, address, minConf);
    }

    public List<ReceivedByAddressInfo> listReceivedByAddress(Integer minConf, Boolean includeEmpty)
            throws JsonRPCStatusException, IOException {
        JavaType resultType = mapper.getTypeFactory().constructCollectionType(List.class, ReceivedByAddressInfo.class);
        return send("listreceivedbyaddress", resultType, minConf, includeEmpty);
    }

    /**
     * Returns a list of unspent transaction outputs with at least one confirmation.
     *
     * @return The unspent transaction outputs
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<UnspentOutput> listUnspent() throws JsonRPCStatusException, IOException {
        return listUnspent(null, null, null);
    }

    /**
     * Returns a list of unspent transaction outputs with at least {@code minConf} and not more than {@code maxConf}
     * confirmations.
     *
     * @param minConf The minimum confirmations to filter
     * @param maxConf The maximum confirmations to filter
     * @return The unspent transaction outputs
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<UnspentOutput> listUnspent(Integer minConf, Integer maxConf)
            throws JsonRPCStatusException, IOException {
        return listUnspent(minConf, maxConf, null);
    }

    /**
     * Returns a list of unspent transaction outputs with at least {@code minConf} and not more than {@code maxConf}
     * confirmations, filtered by a list of addresses.
     *
     * @param minConf The minimum confirmations to filter
     * @param maxConf The maximum confirmations to filter
     * @param filter  Include only transaction outputs to the specified addresses
     * @return The unspent transaction outputs
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<UnspentOutput> listUnspent(Integer minConf, Integer maxConf, Iterable<Address> filter)
            throws JsonRPCStatusException, IOException {
        JavaType resultType = mapper.getTypeFactory().constructCollectionType(List.class, UnspentOutput.class);
        return send("listunspent", resultType, minConf, maxConf, filter);
    }

    /**
     * Returns details about an unspent transaction output.
     *
     * @param txid The transaction hash
     * @param vout The transaction output index
     * @return Details about an unspent output or nothing, if the output was already spent
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public TxOutInfo getTxOut(Sha256Hash txid, Integer vout) throws JsonRPCStatusException, IOException {
        return getTxOut(txid, vout, null);
    }

    /**
     * Returns details about an unspent transaction output.
     *
     * @param txid              The transaction hash
     * @param vout              The transaction output index
     * @param includeMemoryPool Whether to included the memory pool
     * @return Details about an unspent output or nothing, if the output was already spent
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public TxOutInfo getTxOut(Sha256Hash txid, Integer vout, Boolean includeMemoryPool)
            throws JsonRPCStatusException, IOException {
        return send("gettxout", TxOutInfo.class, txid, vout, includeMemoryPool);
    }

    public Coin getUnconfirmedBalance() throws JsonRPCStatusException, IOException {
        return send("getunconfirmedbalance", Coin.class);
    }

    /**
     * Get the balance for a the default Bitcoin "account"
     *
     * @return Is now returning `Coin`, if you need to convert use `BitcoinMath.btcToCoin(result)`
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Coin getBalance() throws JsonRPCStatusException, IOException {
        return getBalance(null, null);
    }

    /**
     * Get the balance for a Bitcoin "account"
     *
     * @param account A Bitcoin "account". (Be wary of using this feature.)
     * @return Is now returning `Coin`, if you need to convert use `BitcoinMath.btcToCoin(result)`
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Coin getBalance(String account) throws JsonRPCStatusException, IOException {
        return getBalance(account, null);
    }

    /**
     * Get the balance for a Bitcoin "account"
     *
     * @param account A Bitcoin "account". (Be wary of using this feature.)
     * @param minConf minimum number of confirmations
     * @return Is now returning `Coin`, if you need to convert use `BitcoinMath.btcToCoin(result)`
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Coin getBalance(String account, Integer minConf) throws JsonRPCStatusException, IOException {
        return send("getbalance", Coin.class, account, minConf);
    }

    public Sha256Hash sendToAddress(Address address, Coin amount) throws JsonRPCStatusException, IOException {
        return sendToAddress(address, amount, null, null);
    }

    public Sha256Hash sendToAddress(Address address, Coin amount, String comment, String commentTo)
            throws JsonRPCStatusException, IOException {
        return send("sendtoaddress", Sha256Hash.class, address, amount, comment, commentTo);
    }

    public Sha256Hash sendFrom(String account, Address address, Coin amount)
            throws JsonRPCStatusException, IOException {
        return send("sendfrom", Sha256Hash.class, account, address, amount);
    }

    public Sha256Hash sendMany(String account, Map<Address, Coin> amounts) throws JsonRPCStatusException, IOException {
        return send("sendmany", Sha256Hash.class, account, amounts);
    }

    /**
     * Set the transaction fee per kB.
     *
     * @param amount The transaction fee in BTC/kB rounded to the nearest 0.00000001.
     * @return True if successful
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Boolean setTxFee(Coin amount) throws JsonRPCStatusException, IOException {
        return send("settxfee", amount);
    }

    public WalletTransactionInfo getTransaction(Sha256Hash txid) throws JsonRPCStatusException, IOException {
        return send("gettransaction", WalletTransactionInfo.class, txid);
    }

    /**
     * Deprecated getinfo function
     *
     * Use GetBlockChainInfo and other alternatives instead
     *
     * @return Structure with various info fields
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    @Deprecated
    public ServerInfo getInfo() throws JsonRPCStatusException, IOException {
        return send("getinfo", ServerInfo.class);
    }

    /**
     * The getblockchaininfo RPC provides information about the current state of the block chain.
     *
     * @return An object containing information about the current state of the block chain.
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public BlockChainInfo getBlockChainInfo() throws JsonRPCStatusException, IOException {
        return send("getblockchaininfo", BlockChainInfo.class);
    }

    /**
     * The getnetworkinfo RPC returns information about the node’s connection to the network.
     *
     * @return information about the node’s connection to the network
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public NetworkInfo getNetworkInfo() throws JsonRPCStatusException, IOException  {
        return send("getnetworkinfo", NetworkInfo.class);
    }

    public WalletInfo getWalletinfo() throws JsonRPCStatusException, IOException {
        return send("getwalletinfo", WalletInfo.class);
    }

    /**
     * Returns list of related addresses
     * Also useful for finding all change addresses in the wallet
     * @return a lost of address groupings
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<List<AddressGroupingItem>>  listAddressGroupings() throws JsonRPCStatusException, IOException {
        // TODO: I'm not sure how to make Jackson mapping work automatically here.
        List<List<List<Object>>> raw = send("listaddressgroupings");
        List<List<AddressGroupingItem>> result = new ArrayList<>();
        for (List<List<Object>> rawGrouping : raw) {
            List<AddressGroupingItem> grouping = new ArrayList<>();
            for (List<Object> addressItem : rawGrouping) {
                AddressGroupingItem item = new AddressGroupingItem(addressItem, getNetParams());
                grouping.add(item);
            }
            result.add(grouping);
        }
        return result;
    }

    public Map listAccounts() throws JsonRPCStatusException, IOException {
        // TODO: I'm not sure how to make Jackson mapping work automatically here.
        Map raw = send("listaccounts", Map.class);
        return raw;
    }

    /**
     * Returns a human readable list of available commands.
     * <p>
     * Bitcoin Core 0.9 returns an alphabetical list of commands, and Bitcoin Core 0.10 returns a categorized list of
     * commands.
     *
     * @return The list of commands as string
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public String help() throws JsonRPCStatusException, IOException {
        return help(null);
    }

    /**
     * Returns helpful information for a specific command.
     *
     * @param command The name of the command to get help for
     * @return The help text
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public String help(String command) throws JsonRPCStatusException, IOException {
        return send("help", command);
    }

    /**
     * Returns a list of available commands.
     *
     * Commands which are unavailable will not be listed, such as wallet RPCs, if wallet support is disabled.
     *
     * @return The list of commands
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<String> getCommands() throws JsonRPCStatusException, IOException {
        List<String> commands = new ArrayList<String>();
        for (String entry : help().split("\n")) {
            if (!entry.isEmpty() && !entry.matches("== (.+) ==")) {
                String command = entry.split(" ")[0];
                commands.add(command);
            }
        }
        return commands;
    }

    /**
     * Checks whether a command exists.
     *
     * This is done indirectly, by using {help(String) help} to get information about the command, and if information
     * about the command is available, then the command exists. The absence of information does not necessarily imply
     * the non-existence of a command.
     *
     * @param command The name of the command to check
     * @return True if the command exists
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public Boolean commandExists(String command) throws JsonRPCStatusException, IOException {
        return !help(command).contains("help: unknown command");
    }

    /**
     * Permanently marks a block as invalid, as if it violated a consensus rule.
     *
     * @param hash The block hash
     * @since Bitcoin Core 0.10
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public void invalidateBlock(Sha256Hash hash) throws JsonRPCStatusException, IOException {
        send("invalidateblock", hash);
    }

    /**
     * Removes invalidity status of a block and its descendants, reconsider them for activation.
     * <p>
     * This can be used to undo the effects of {link invalidateBlock(Sha256Hash) invalidateBlock}.
     *
     * @param hash The hash of the block to reconsider
     * @since Bitcoin Core 0.10
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public void reconsiderBlock(Sha256Hash hash) throws JsonRPCStatusException, IOException {
        send("reconsiderblock", hash);
    }

    /**
     * Return information about all known tips in the block tree, including the main chain as well as orphaned branches.
     *
     * @return A list of chain tip information
     * @since Bitcoin Core 0.10
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<ChainTip> getChainTips() throws JsonRPCStatusException, IOException {
        JavaType resultType = mapper.getTypeFactory().constructCollectionType(List.class, ChainTip.class);
        return send("getchaintips",resultType);
    }


    /**
     * Attempt to add or remove a node from the addnode list, or to try a connection to a node once.
     *
     * @param node node to add as a string in the form of <IP address>:<port>
     * @param command `add`, `remove`, or `onetry`
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public void addNode(String node, String command) throws JsonRPCStatusException, IOException {
        send("addnode", node, command);
    }

    /**
     * Return information about the given added node
     *
     * @param details `true` to return detailed information
     * @param node the node to provide information about
     * @return A Jackson JsonNode object (until we define a POJO)
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public JsonNode getAddedNodeInfo(boolean details, String node) throws JsonRPCStatusException, IOException  {
        return send("getaddednodeinfo", JsonNode.class, details, node);
    }

    /**
     * Return information about all added nodes
     *
     * @param details `true` to return detailed information
     * @return A Jackson JsonNode object (until we define a POJO)
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public JsonNode getAddedNodeInfo(boolean details) throws JsonRPCStatusException, IOException  {
        return getAddedNodeInfo(details, null);
    }

    /**
     * Clears the memory pool and returns a list of the removed transactions.
     *
     * Note: this is a customized command, which is currently not part of Bitcoin Core.
     * See https://github.com/OmniLayer/OmniJ/pull/72[Pull Request #72] on GitHub
     *
     * @return A list of transaction hashes of the removed transactions
     * @throws JsonRPCStatusException JSON RPC status exception
     * @throws IOException network error
     */
    public List<Sha256Hash> clearMemPool() throws JsonRPCStatusException, IOException {
        JavaType resultType = mapper.getTypeFactory().constructCollectionType(List.class, Sha256Hash.class);
        return send("clearmempool", resultType);
    }
}