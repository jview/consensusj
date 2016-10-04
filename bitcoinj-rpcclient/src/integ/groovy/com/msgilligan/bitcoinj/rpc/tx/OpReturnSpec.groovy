package com.msgilligan.bitcoinj.rpc.tx

import com.msgilligan.bitcoinj.BaseRegTestSpec
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes
import spock.lang.Stepwise
import spock.lang.Unroll

/**
 *  Create, send, record, and retrieve an OP_RETURN transaction
 */
class OpReturnSpec extends TxTestBaseSpec {
    @Unroll
    def "create and send a bitcoinj OP_RETURN transaction using #methodName"(submitMethod, methodName) {
        given: "data for the OP_RETURN, transaction ingredients, a destination address and an amount to send"
        def length = 80
        def testData = 0..<length as byte[]

        def ingredients = createIngredients(50.btc)
        // We're going to spend a lot of (fake) BTC to write these 80 bytes!
        Coin amount = 49.999.btc

        when: "we build a transaction"
        Transaction tx = new Transaction(params)
        Script script = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(testData)
                .build()
        tx.addOutput(amount, script)

        // Assume only 1 (first) outpoint is needed (assuming utxos made by createIngredients are big enough)
        tx.addSignedInput(ingredients.outPoints.get(0), ScriptBuilder.createOutputScript(ingredients.address), ingredients.privateKey);

        and: "send via submitMethod [P2P, RPC] and generate a block"
        Transaction sentTx = submitMethod(tx)

        then: "we can retrieve and verify the data"
        with (sentTx.getOutput(0).scriptPubKey.chunks.get(0)) {
            opcode == ScriptOpCodes.OP_RETURN
        }
        with (sentTx.getOutput(0).scriptPubKey.chunks.get(1)) {
            opcode == opCodeFromLength(testData.length);
            data == testData
        }

        where: "submitMethod is P2P and then RPC"
        [submitMethod, methodName] << submitMethods
    }

    private static int opCodeFromLength(int length) {
        return (length >= ScriptOpCodes.OP_PUSHDATA1) ? ScriptOpCodes.OP_PUSHDATA1 : length
    }


}
