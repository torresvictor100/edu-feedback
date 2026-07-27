package br.com.edufeedback.relatorio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.edufeedback.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class RelatorioIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveRejeitarConsultaSemToken() throws Exception {
        mockMvc.perform(get("/relatorios/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornarNaoEncontradoParaIdInexistenteComTokenValido() throws Exception {
        String token = obterTokenAdmin();

        mockMvc.perform(get("/relatorios/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRejeitarListagemSemToken() throws Exception {
        mockMvc.perform(get("/relatorios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveListarRelatoriosComTokenValido() throws Exception {
        String token = obterTokenAdmin();

        mockMvc.perform(get("/relatorios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deveRetornarRequisicaoInvalidaParaIdMalformado() throws Exception {
        String token = obterTokenAdmin();

        mockMvc.perform(get("/relatorios/nao-e-um-uuid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private String obterTokenAdmin() throws Exception {
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@edufeedback.local\", \"senha\": \"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody).get("token").asText();
    }
}
