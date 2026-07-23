package com.obdinsight.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AiHypothesis(
    val name: String?,
    val unit: String?,
    val formula: String?,
    val confidence: String?,
    val reasoning: String,
    val sources: List<Pair<String, String>>,
    val error: String? = null,
)

/**
 * Asks Claude - with the web-search tool enabled - to reason about what an unrecognized OBD-II
 * Mode 01 PID or manufacturer-specific Mode 22 DID most likely represents on this vehicle,
 * using whatever it can find online (Ross-Tech/VCDS references, community DID lists, TunerPro/
 * RomRaider definitions, forum reverse-engineering write-ups, etc.) plus its own reasoning about
 * the byte pattern in the captured response.
 *
 * This calls the Anthropic API directly from the device using a user-supplied API key (see
 * [SecurePrefs]) - there is no backend server. That is a deliberate BYOK (bring your own key)
 * design so the app has no server component to run or trust, but it does mean the key lives on
 * the user's own device; only enter a key you're comfortable being used this way.
 */
class ClaudeAnalyzer(private val apiKey: String) {

    suspend fun identify(
        vehicleContext: String,
        mode: Int?,
        pid: Int?,
        requestHex: String,
        responseRaw: String,
    ): AiHypothesis = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AiHypothesis(
                name = null, unit = null, formula = null, confidence = null,
                reasoning = "", sources = emptyList(),
                error = "No Anthropic API key configured. Add one in Settings to enable AI analysis.",
            )
        }

        try {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            val prompt = buildPrompt(vehicleContext, mode, pid, requestHex, responseRaw)

            val params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_8)
                .maxTokens(4096L)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())
                .addTool(WebSearchTool20260209.builder().maxUses(5L).build())
                .addUserMessage(prompt)
                .build()

            val response = client.messages().create(params)

            val text = StringBuilder()
            response.content().forEach { block ->
                block.text().ifPresent { text.append(it.text()) }
            }
            parseHypothesis(text.toString())
        } catch (e: Exception) {
            AiHypothesis(
                name = null, unit = null, formula = null, confidence = null,
                reasoning = "", sources = emptyList(),
                error = "AI analysis failed: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    private fun buildPrompt(
        vehicleContext: String,
        mode: Int?,
        pid: Int?,
        requestHex: String,
        responseRaw: String,
    ): String {
        val modeDesc = mode?.let { "0x%02X".format(it) } ?: "unknown"
        val pidDesc = pid?.let { "0x%02X".format(it) } ?: "unknown"
        return """
            You are helping identify an unrecognized OBD-II / UDS diagnostic parameter captured
            live from a real vehicle over a Bluetooth OBD2 dongle (ELM327-compatible command set).

            Vehicle: $vehicleContext

            Captured exchange:
            - Request sent (hex, as sent after ELM327 line-ending handling): $requestHex
            - Mode: $modeDesc   PID/DID: $pidDesc
            - Raw response bytes received: $responseRaw

            This PID/DID is not in the standard SAE J1979 Mode 01 table, so it's either a
            manufacturer-specific parameter (common for Mode 22 "ReadDataByIdentifier" requests
            on VAG-group ECUs) or a standard PID this app doesn't have decode logic for yet.

            Use web search to check community reverse-engineering resources where relevant -
            Ross-Tech/VCDS measuring-block and DID references, TunerPro/RomRaider definition
            files, OpenPort/OBD forums, GitHub UDS DID lists, and general VAG EA189/EA288 TDI
            engine diagnostics write-ups - focusing on this exact ECU/engine where possible and
            falling back to closely related VAG common-rail diesel engines otherwise.

            Then give your best-effort hypothesis for what sensor or metric this represents, its
            likely unit, and (if the byte pattern and any formula you found support it) how the
            raw bytes scale to a physical value. Be explicit about your confidence - manufacturer
            codes for this exact ECU software version are frequently undocumented publicly, and a
            clearly-labeled reasonable guess is more useful here than false confidence.

            Respond in exactly two parts:
            1. A short, readable explanation of your reasoning. Cite any web sources you used as
               markdown links, e.g. [Ross-Tech VCDS wiki](https://...).
            2. A fenced ```json code block, and nothing after it, containing exactly this shape:
               {"name": "<short sensor/metric name, or null>", "unit": "<likely unit, or null>",
                "formula": "<plain-language byte-to-value formula, or null>",
                "confidence": "high|medium|low"}
        """.trimIndent()
    }

    private fun parseHypothesis(text: String): AiHypothesis {
        val jsonMatch = Regex("```json\\s*(\\{[\\s\\S]*?})\\s*```").find(text)
        val json = jsonMatch?.groupValues?.get(1)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val sources = Regex("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)")
            .findAll(text)
            .map { it.groupValues[1] to it.groupValues[2] }
            .distinct()
            .toList()

        fun JSONObject?.stringOrNull(key: String): String? {
            if (this == null || isNull(key)) return null
            val v = optString(key, "")
            return v.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        }

        val reasoning = jsonMatch?.range?.first?.let { text.substring(0, it).trim() } ?: text.trim()
        return AiHypothesis(
            name = json.stringOrNull("name"),
            unit = json.stringOrNull("unit"),
            formula = json.stringOrNull("formula"),
            confidence = json.stringOrNull("confidence"),
            reasoning = reasoning.ifBlank { text.trim() },
            sources = sources,
        )
    }
}
