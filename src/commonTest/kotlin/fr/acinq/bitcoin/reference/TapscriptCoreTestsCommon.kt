package fr.acinq.bitcoin.reference

import fr.acinq.bitcoin.*
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.json.*
import org.kodein.memory.file.*
import org.kodein.memory.text.readString
import org.kodein.memory.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * this is a "port" of https://github.com/bitcoin/bitcoin/blob/master/test/functional/feature_taproot.py
 * test data is generated by running bitcoin core's test with the `--dumptests` option (this will create a
 * set of directories `0`,`1`,`1`,...`a`,`b`,...`f` which can be copied into `commonTest/resources/data/taproot-functional-tests`
 * these test vectors are randomized (bitcoin core will generate a new set of test data on each run)
 */
class TapscriptCoreTestsCommon {
    @Test
    fun `tapscript tests`() {
        var count = 0
        TransactionTestsCommon.resourcesDir().resolve("data").resolve("taproot-functional-tests").listDir().forEach { dir ->
            dir.listDir().forEach {
                val json = readJson(it)
                run(json, it.name)
                count++
            }
        }
        assertEquals(count, 2760)
    }

    private fun readJson(path: Path): JsonObject {
        val format = Json { ignoreUnknownKeys = true }
        var raw = path.openReadableFile().use { it.readString() }.filterNot { c -> c == '\n' }
        if (raw.last() == ',') {
            raw = raw.dropLast(1)
        }

        return format.parseToJsonElement(raw).jsonObject
    }

    @Test
    fun `single test`() {
        val file = TransactionTestsCommon.resourcesDir().resolve("data").resolve("taproot-functional-tests").resolve("3").resolve("3c16caf4303dc387d0e90aa1266d0e4e1bf92ffc")
        val json = readJson(file)
        run(json, file.name)
    }

    fun run(json: JsonObject, name: String) {
        json["success"]?.let { runInternal(json, it.jsonObject, name, shouldSucceed = true) }
        json["failure"]?.let { runInternal(json, it.jsonObject, name, shouldSucceed = false) }
    }

    private fun runInternal(json: JsonObject, inputData: JsonObject, name: String, shouldSucceed: Boolean) {
        val tx = Transaction.read(json["tx"]!!.jsonPrimitive.content)
        val prevouts = json["prevouts"]!!.jsonArray.map { TxOut.read(it.jsonPrimitive.content) }
        val witness = inputData.jsonObject["witness"]!!.jsonArray.map { ByteVector.fromHex(it.jsonPrimitive.content) }
        val scriptSig = Hex.decode(inputData.jsonObject["scriptSig"]!!.jsonPrimitive.content)
        val i = json["index"]!!.jsonPrimitive.int
        val tx1 = tx
            .updateWitness(i, ScriptWitness(witness))
            .updateSigScript(i, scriptSig)
        val prevOutput = prevouts[i]
        val prevOutputScript = prevOutput.publicKeyScript
        val amount = prevOutput.amount
        val scriptFlags = ScriptTestsCommon.parseScriptFlags(json["flags"]!!.jsonPrimitive.content)
        val ctx = Script.Context(tx1, i, amount, prevouts)
        val runner = Script.Runner(ctx, scriptFlags, null)
        if (shouldSucceed) {
            val result = runner.verifyScripts(tx1.txIn[i].signatureScript, prevOutputScript, tx1.txIn[i].witness)
            assertTrue(result, "success test $name failed")
        } else {
            val result = kotlin.runCatching {
                runner.verifyScripts(tx1.txIn[i].signatureScript, prevOutputScript, tx1.txIn[i].witness)
            }.getOrDefault(false)
            assertFalse(result, "failure test $name failed")
        }
    }
}