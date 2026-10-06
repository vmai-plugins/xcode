package digital.vmstudio.code.core.ai.omniroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayModelPricingTest {

    @Test
    fun `zero pricing, a free flag, tier or tag, and the free suffix all count as free`() {
        val free = GatewayModelPricing.parseFreeModels(
            """{"data":[
                {"id":"paid","pricing":{"prompt":"0.000002","completion":"0.000006"}},
                {"id":"or-free","pricing":{"prompt":"0","completion":"0"}},
                {"id":"flagged","free":true},
                {"id":"tiered","tier":"free"},
                {"id":"tagged","tags":["fast","free"]},
                {"id":"meta/llama-3.3-70b:free"}
            ]}""",
        )
        assertEquals(setOf("or-free", "flagged", "tiered", "tagged", "meta/llama-3.3-70b:free"), free)
    }

    @Test
    fun `free by name`() {
        assertTrue(GatewayModelPricing.isFreeModelId("deepseek/deepseek-r1:free"))
        assertTrue(GatewayModelPricing.isFreeModelId("qwen-coder-free"))
        assertFalse(GatewayModelPricing.isFreeModelId("gpt-4o"))
        assertFalse(GatewayModelPricing.isFreeModelId("freedom-7b"))
    }
}
