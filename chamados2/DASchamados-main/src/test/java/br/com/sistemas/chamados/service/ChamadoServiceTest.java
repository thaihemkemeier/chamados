package br.com.sistemas.chamados.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.sistemas.chamados.dto.AlterarStatusRequest;
import br.com.sistemas.chamados.dto.AtribuirTecnicoRequest;
import br.com.sistemas.chamados.dto.ChamadoRequest;
import br.com.sistemas.chamados.dto.ChamadoResponse;
import br.com.sistemas.chamados.entity.Categoria;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.entity.Prioridade;
import br.com.sistemas.chamados.entity.StatusChamado;
import br.com.sistemas.chamados.exception.ChamadoFechadoException;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.repository.CategoriaRepository;
import br.com.sistemas.chamados.repository.ChamadoRepository;
import br.com.sistemas.chamados.repository.ClienteRepository;
import br.com.sistemas.chamados.repository.ComentarioRepository;
import br.com.sistemas.chamados.repository.TecnicoRepository;

@ExtendWith(MockitoExtension.class)
class ChamadoServiceTest {

    @Mock
    private ChamadoRepository repository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private TecnicoRepository tecnicoRepository;
    @Mock
    private ComentarioRepository comentarioRepository;

    @InjectMocks
    private ChamadoService service;

    private Chamado novoChamado() {
        return new Chamado("PC não liga", "Sem imagem", Prioridade.ALTA,
            new Cliente("Ana", "ana@exemplo.com", "47999999999"),
            new Categoria("Hardware", null));
    }

    @Test
    void criarChamadoNasceAbertoComDataDeAbertura() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(
            new Cliente("Ana", "ana@exemplo.com", "47999999999")));
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(
            new Categoria("Hardware", null)));
        when(repository.save(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        ChamadoResponse resposta = service.criar(
            new ChamadoRequest("PC não liga", "Sem imagem", Prioridade.ALTA, 1L, 2L));

        assertEquals(StatusChamado.ABERTO, resposta.status());
        assertNotNull(resposta.dataAbertura());
        assertNull(resposta.dataFechamento());
        assertNull(resposta.tecnicoId());
    }

    @Test
    void criarComClienteInexistenteLancaNaoEncontrado() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () ->
            service.criar(new ChamadoRequest("T", "D", Prioridade.BAIXA, 99L, 1L)));
    }

    @Test
    void transicaoValidaAtualizaStatus() {
        Chamado chamado = novoChamado();
        when(repository.findById(1L)).thenReturn(Optional.of(chamado));

        service.alterarStatus(1L, new AlterarStatusRequest(StatusChamado.EM_ATENDIMENTO));

        assertEquals(StatusChamado.EM_ATENDIMENTO, chamado.getStatus());
        assertNull(chamado.getDataFechamento());
    }

    @Test
    void fecharChamadoPreencheDataFechamento() {
        Chamado chamado = novoChamado();
        when(repository.findById(1L)).thenReturn(Optional.of(chamado));

        service.alterarStatus(1L, new AlterarStatusRequest(StatusChamado.FECHADO));

        assertEquals(StatusChamado.FECHADO, chamado.getStatus());
        assertNotNull(chamado.getDataFechamento());
    }

    @Test
    void transicaoInvalidaLancaRegraNegocio() {
        Chamado chamado = novoChamado(); // ABERTO
        when(repository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThrows(RegraNegocioException.class, () ->
            service.alterarStatus(1L, new AlterarStatusRequest(StatusChamado.AGUARDANDO_CLIENTE)));
        assertEquals(StatusChamado.ABERTO, chamado.getStatus());
    }

    @Test
    void chamadoFechadoNaoPodeMudarDeStatus() {
        Chamado chamado = novoChamado();
        chamado.setStatus(StatusChamado.FECHADO);
        when(repository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThrows(ChamadoFechadoException.class, () ->
            service.alterarStatus(1L, new AlterarStatusRequest(StatusChamado.ABERTO)));
    }

    @Test
    void chamadoFechadoNaoAceitaAtribuicaoDeTecnico() {
        Chamado chamado = novoChamado();
        chamado.setStatus(StatusChamado.FECHADO);
        when(repository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThrows(ChamadoFechadoException.class, () ->
            service.atribuir(1L, new AtribuirTecnicoRequest(5L)));
    }
}
