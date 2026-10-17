package com.pinliao.backend.common.web;

import com.pinliao.backend.demo.DemoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T0 骨架自检：统一包络 / traceId / 异常映射 / 参数校验（不依赖数据库与 Redis）。 */
@WebMvcTest(controllers = DemoController.class)
class EnvelopeSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 成功响应_包络字段齐全且traceId非空() throws Exception {
        mockMvc.perform(get("/api/v1/_demo/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.pong").value(true))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void 业务异常_按业务码映射HTTP状态且data为null() throws Exception {
        mockMvc.perform(get("/api/v1/_demo/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void 参数校验失败_返回422与errors数组() throws Exception {
        mockMvc.perform(get("/api/v1/_demo/validate").param("phone", "123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201))
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message").value("手机号格式不正确"));
    }
}
