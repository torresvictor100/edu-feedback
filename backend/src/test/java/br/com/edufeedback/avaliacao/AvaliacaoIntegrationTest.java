package br.com.edufeedback.avaliacao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.edufeedback.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AvaliacaoIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveAceitarAvaliacaoComNotaAltaComoNormal() throws Exception {
        mockMvc.perform(post("/avaliação")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Aula muito boa\", \"nota\": 9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urgencia").value("NORMAL"));
    }

    @Test
    void deveMarcarAvaliacaoComNotaBaixaComoCritica() throws Exception {
        mockMvc.perform(post("/avaliação")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Não entendi nada da aula\", \"nota\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urgencia").value("CRITICA"));
    }

    @Test
    void deveRejeitarNotaForaDoIntervalo() throws Exception {
        mockMvc.perform(post("/avaliação")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Nota inválida\", \"nota\": 15}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarDescricaoEmBranco() throws Exception {
        mockMvc.perform(post("/avaliação")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"\", \"nota\": 5}"))
                .andExpect(status().isBadRequest());
    }
}
