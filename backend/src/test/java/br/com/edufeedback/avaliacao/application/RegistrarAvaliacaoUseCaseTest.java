package br.com.edufeedback.avaliacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.edufeedback.avaliacao.domain.Avaliacao;
import br.com.edufeedback.avaliacao.domain.AvaliacaoRepository;
import br.com.edufeedback.avaliacao.domain.NotificacaoCriticaPublisher;
import br.com.edufeedback.avaliacao.domain.Urgencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrarAvaliacaoUseCaseTest {

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private NotificacaoCriticaPublisher notificacaoCriticaPublisher;

    @Test
    void deveMarcarComoCriticaQuandoNotaMenorOuIgualAoLimiteEPublicarNotificacao() {
        RegistrarAvaliacaoUseCase useCase =
                new RegistrarAvaliacaoUseCase(avaliacaoRepository, notificacaoCriticaPublisher, 3);
        when(avaliacaoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Avaliacao resultado = useCase.registrar("aula ruim", 2);

        assertThat(resultado.getUrgencia()).isEqualTo(Urgencia.CRITICA);
        verify(notificacaoCriticaPublisher, times(1)).publicar(resultado);
    }

    @Test
    void naoDevePublicarNotificacaoQuandoNotaAcimaDoLimite() {
        RegistrarAvaliacaoUseCase useCase =
                new RegistrarAvaliacaoUseCase(avaliacaoRepository, notificacaoCriticaPublisher, 3);
        when(avaliacaoRepository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Avaliacao resultado = useCase.registrar("aula boa", 8);

        assertThat(resultado.getUrgencia()).isEqualTo(Urgencia.NORMAL);
        verify(notificacaoCriticaPublisher, never()).publicar(any());
    }
}
