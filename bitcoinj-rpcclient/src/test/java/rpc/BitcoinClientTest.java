package rpc;

import com.msgilligan.bitcoinj.json.pojo.ChainTip;
import com.msgilligan.bitcoinj.json.pojo.RawTransactionInfo;
import com.msgilligan.bitcoinj.json.pojo.UnspentOutput;
import com.msgilligan.bitcoinj.json.pojo.WalletTransactionInfo;
import com.msgilligan.bitcoinj.rpc.BitcoinClient;
import com.msgilligan.bitcoinj.rpc.BitcoinExtendedClient;
import com.msgilligan.bitcoinj.rpc.RPCConfig;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;


public class BitcoinClientTest {
    private static BitcoinClient client;

    private static BitcoinExtendedClient extendedClient;

    @BeforeClass
    public static void init() throws Exception {

        RPCConfig rpcConfig = new RPCConfig("testnet", "http://10.0.1.175:18332", "justin", "xy2ljqIsI80FNirLjtk1X8RA5N6qU5TVroOSVzJOH-o=");
        client = new BitcoinClient(rpcConfig);

        extendedClient=new BitcoinExtendedClient(rpcConfig);
    }

    @Test
    public void getWalletinfo() throws Exception{
        Object obj=extendedClient.getWalletinfo();
        System.out.println(obj);
    }

    @Test
    public void getBlockCount() throws Exception {
        Object obj = extendedClient.getBlockCount();
        System.out.println(obj);
    }

    @Test
    public void getBalance() throws Exception {
        Object obj = extendedClient.getBalance();
        System.out.println(obj);
    }



    @Test
    public void getUnconfirmedBalance() throws Exception {
        Object obj = extendedClient.getUnconfirmedBalance();
        System.out.println(obj);
    }

    @Test
    public void getBitcoinBalance() throws Exception {
        Address address=new Address(extendedClient.getNetParams(), "2N3kYabjh6THxGUcgfnHBxwqmFiYfmyaND4");
        Object obj = extendedClient.getBitcoinBalance(address);
        System.out.println(obj);
    }

    @Test
    public void getBitcoinBalance2() throws Exception {
        Address address=new Address(extendedClient.getNetParams(), "2MsNZuqSaNmDTLU95aKNCHYUbUEU7PxCzo4");
        Object obj = extendedClient.getBitcoinBalance(address);
        System.out.println(obj);
    }

    @Test
    public void getBlockChainInfo() throws Exception {
        Object obj = extendedClient.getBlockChainInfo();
        System.out.println(obj);
    }

    @Test
    public void getChainTips() throws Exception {
        List<ChainTip> obj = extendedClient.getChainTips();

        obj.stream().forEach(e->{
            System.out.println(e.getHash()+" "+e.getStatus());
        });
    }

    @Test
    public void getAccountAddress() throws Exception {

        Object obj =extendedClient.getAccountAddress("2MzFtWms6VRkbruK8m449A34vEGuagLif4R");
        System.out.println(obj);
    }

    @Test
    public void getNetworkInfo() throws Exception{
        Object obj=extendedClient.getNetworkInfo();
        System.out.println(obj);
    }

    @Test
    public void getNetParams() throws Exception{
        Object obj=extendedClient.getNetParams();
        System.out.println(obj);
    }

    @Test
    public void getNewAddress() throws Exception{
        Object obj=extendedClient.getNewAddress();
        System.out.println(obj);
    }

    @Test
    public void getBlock() throws Exception{
        Object obj=extendedClient.getBlock(1325894);
        System.out.println(obj);
    }

    @Test
    public void getBlockHash() throws Exception{
        Object obj=extendedClient.getBlockHash(1325894);
        System.out.println(obj);
    }

    @Test
    public void getBlockInfo() throws Exception{
        Object obj=extendedClient.getBlockInfo(Sha256Hash.wrap("00000000011725cd2c05ca49474aca2c56d6d14849119cf87ef2a111482d705b"));
        System.out.println(obj);
    }

    @Test
    public void listAddressGroupings() throws Exception{
        Object obj=extendedClient.listAddressGroupings();
        System.out.println(obj);
    }

    @Test
    public void listAccounts() throws Exception{
        Object obj=extendedClient.listAccounts();
        System.out.println(obj);
    }


    @Test
    public void getTransaction() throws Exception{
        WalletTransactionInfo obj=extendedClient.getTransaction(Sha256Hash.wrap("dd4bf1feb8187be1b389a387b66d5410ebf3d39af83f1cefa95e9ab633e95305"));
        System.out.println("confirms="+obj.getConfirmations()+" fee="+obj.getFee()+" blockindex="+obj.getBlockindex());
        obj=extendedClient.getTransaction(Sha256Hash.wrap("d092a2217bac43c5d735ac048aa957ae683eb94727a3bdcdda2ffa75cc65d8c3"));
        System.out.println("confirms="+obj.getConfirmations()+" fee="+obj.getFee()+" blockindex="+obj.getBlockindex()+" amount="+obj.getAmount());
    }



    @Test
    public void listUnspent() throws Exception{
        List<UnspentOutput> unspentOutputs=extendedClient.listUnspent();
//        System.out.println(gson.toJson(unspentOutputs));
        for(UnspentOutput unspentOutput:unspentOutputs){
            System.out.println("confirms="+unspentOutput.getConfirmations()+" amount="+unspentOutput.getAmount()+" account="+unspentOutput.getAccount()+" address="+unspentOutput.getAddress());
        }

    }

    @Test
    public void getRawTransaction() throws Exception{
        Transaction obj=extendedClient.getRawTransaction(Sha256Hash.wrap("d092a2217bac43c5d735ac048aa957ae683eb94727a3bdcdda2ffa75cc65d8c3"));
        System.out.println(obj.getFee());
    }

    @Test
    public void getRawTransactionInfo() throws Exception{
        RawTransactionInfo obj=extendedClient.getRawTransactionInfo(Sha256Hash.wrap("51f5fc604bce4a50815b1e4c90ac267702883b0ac09a5a95b25be3c208620c81"));
        System.out.println("confirms="+obj.getConfirmations());
//        System.out.println(obj);
    }

//    @Test
//    public void getAddedNodeInfo() throws Exception{
//        Object obj=extendedClient.getAddedNodeInfo(true);
//        System.out.println(obj);
//    }

//    @Test
//    public void sendToAddress() throws Exception{
//        Address address=new Address(extendedClient.getNetParams(), "2N8jx97VfGX3vVPWM74vYMiCwebc5kwemY8");
//        Object obj=extendedClient.sendToAddress(address, Coin.parseCoin("0.1"));
//        System.out.println(obj);
//    }

    @Test
    public void testCoin(){
        System.out.println(Coin.parseCoin("0.1"));
    }


}
