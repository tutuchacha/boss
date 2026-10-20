package com.pinliao.backend.ai.provider;

import com.pinliao.backend.ai.spi.AiChatClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-AI3：假对话模型的流式回放（分段、拼接一致、非空）。
 */
class FakeChatClientTest {

    private final FakeChatClient client = new FakeChatClient();

    @Test
    void 流式分多段回放_拼接后与非流式完全一致() {
        List<String> deltas = new ArrayList<>();

        AiChatClient.ChatResult result = client.stream("系统", "用户", deltas::add);

        assertTrue(deltas.size() > 1, "应分多段回放，实际 " + deltas.size());
        assertTrue(deltas.stream().noneMatch(String::isEmpty));
        assertEquals(String.join("", deltas), result.text());
        assertEquals(client.chat("系统", "用户").text(), result.text());
        assertTrue(result.text().contains("[1]"));
    }

    @Test
    void 流式结果带固定token统计与模型标识() {
        AiChatClient.ChatResult result = client.stream("系统", "用户", text -> { });

        assertEquals(32, result.totalTokens());
        assertEquals("fake-chat-v1", client.modelId());
        assertFalse(result.text().isBlank());
    }
}
