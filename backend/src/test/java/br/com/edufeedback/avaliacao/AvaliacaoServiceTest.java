package br.com.edufeedback.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private NotificacaoCriticaPublisher notificacaoCriticaPublisher;

    @Test
    void deveMarcarComoCriticaQuandoNotaMenorOuIgualAoLimiteEPublicarNotificacao() {
        AvaliacaoService service = new AvaliacaoService(avaliacaoRepository, notificacaoCriticaPublisher, 3);
        when(avaliacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Avaliacao resultado = service.registrar("aula ruim", 2);

        assertThat(resultado.getUrgencia()).isEqualTo(Urgencia.CRITICA);
        verify(notificacaoCriticaPublisher, times(1)).publicar(resultado);
    }

    @Test
    void naoDevePublicarNotificacaoQuandoNotaAcimaDoLimite() {
        AvaliacaoService service = new AvaliacaoService(avaliacaoRepository, notificacaoCriticaPublisher, 3);
        when(avaliacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Avaliacao resultado = service.registrar("aula boa", 8);

        assertThat(resultado.getUrgencia()).isEqualTo(Urgencia.NORMAL);
        verify(notificacaoCriticaPublisher, never()).publicar(any());
    }
}
