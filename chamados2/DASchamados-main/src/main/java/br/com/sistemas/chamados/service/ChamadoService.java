package br.com.sistemas.chamados.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemas.chamados.dto.AlterarStatusRequest;
import br.com.sistemas.chamados.dto.AtribuirTecnicoRequest;
import br.com.sistemas.chamados.dto.AtualizarChamadoRequest;
import br.com.sistemas.chamados.dto.ChamadoDetalheResponse;
import br.com.sistemas.chamados.dto.ChamadoRequest;
import br.com.sistemas.chamados.dto.ChamadoResponse;
import br.com.sistemas.chamados.dto.ComentarioRequest;
import br.com.sistemas.chamados.dto.ComentarioResponse;
import br.com.sistemas.chamados.dto.PaginaResponse;
import br.com.sistemas.chamados.entity.Categoria;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.entity.Comentario;
import br.com.sistemas.chamados.entity.Prioridade;
import br.com.sistemas.chamados.entity.StatusChamado;
import br.com.sistemas.chamados.entity.Tecnico;
import br.com.sistemas.chamados.exception.ChamadoFechadoException;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.repository.CategoriaRepository;
import br.com.sistemas.chamados.repository.ChamadoRepository;
import br.com.sistemas.chamados.repository.ClienteRepository;
import br.com.sistemas.chamados.repository.ComentarioRepository;
import br.com.sistemas.chamados.repository.TecnicoRepository;
import jakarta.persistence.criteria.Predicate;

@Service
public class ChamadoService {

    private static final Map<StatusChamado, Set<StatusChamado>> TRANSICOES = Map.of(
        StatusChamado.ABERTO,
            EnumSet.of(StatusChamado.EM_ATENDIMENTO, StatusChamado.FECHADO),
        StatusChamado.EM_ATENDIMENTO,
            EnumSet.of(StatusChamado.AGUARDANDO_CLIENTE, StatusChamado.FECHADO),
        StatusChamado.AGUARDANDO_CLIENTE,
            EnumSet.of(StatusChamado.EM_ATENDIMENTO, StatusChamado.FECHADO),
        StatusChamado.FECHADO,
            EnumSet.noneOf(StatusChamado.class)
    );

    private final ChamadoRepository repository;
    private final ClienteRepository clienteRepository;
    private final CategoriaRepository categoriaRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ComentarioRepository comentarioRepository;

    public ChamadoService(ChamadoRepository repository,
                          ClienteRepository clienteRepository,
                          CategoriaRepository categoriaRepository,
                          TecnicoRepository tecnicoRepository,
                          ComentarioRepository comentarioRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.categoriaRepository = categoriaRepository;
        this.tecnicoRepository = tecnicoRepository;
        this.comentarioRepository = comentarioRepository;
    }

    @Transactional
    public ChamadoResponse criar(ChamadoRequest dto) {
        Cliente cliente = buscarCliente(dto.clienteId());
        Categoria categoria = buscarCategoria(dto.categoriaId());
    
        Chamado chamado = new Chamado(
            dto.titulo(), dto.descricao(), dto.prioridade(), cliente, categoria);
        return ChamadoResponse.de(repository.save(chamado));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ChamadoResponse> listar(
            StatusChamado status, Prioridade prioridade, Long clienteId, Pageable pageable) {
        Page<ChamadoResponse> pagina = repository
            .findAll(filtrar(status, prioridade, clienteId), pageable)
            .map(ChamadoResponse::de);
        return PaginaResponse.de(pagina);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ChamadoResponse> listarPorCliente(Long clienteId, Pageable pageable) {
        buscarCliente(clienteId); 
        return listar(null, null, clienteId, pageable);
    }

    @Transactional(readOnly = true)
    public ChamadoDetalheResponse buscar(Long id) {
        Chamado chamado = buscarEntity(id);
        return ChamadoDetalheResponse.de(
            chamado, comentarioRepository.findByChamadoIdOrderByDataAsc(id));
    }

    @Transactional
    public ChamadoResponse atualizar(Long id, AtualizarChamadoRequest dto) {
        Chamado chamado = buscarEntity(id);
        garantirNaoFechado(chamado);
        chamado.setTitulo(dto.titulo());
        chamado.setDescricao(dto.descricao());
        chamado.setPrioridade(dto.prioridade());
        chamado.setCategoria(buscarCategoria(dto.categoriaId()));
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public void excluir(Long id) {
        Chamado chamado = buscarEntity(id);
        comentarioRepository.deleteByChamadoId(id);
        repository.delete(chamado);
    }

    @Transactional
    public ChamadoResponse atribuir(Long id, AtribuirTecnicoRequest dto) {
        Chamado chamado = buscarEntity(id);
        garantirNaoFechado(chamado);
        Tecnico tecnico = tecnicoRepository.findById(dto.tecnicoId())
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Técnico " + dto.tecnicoId() + " não encontrado"));
        chamado.setTecnico(tecnico);
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse alterarStatus(Long id, AlterarStatusRequest dto) {
        Chamado chamado = buscarEntity(id);
        garantirNaoFechado(chamado);

        StatusChamado atual = chamado.getStatus();
        StatusChamado novo = dto.status();
        if (!TRANSICOES.get(atual).contains(novo)) {
            throw new RegraNegocioException(
                "Transição de status inválida: " + atual + " -> " + novo);
        }

        chamado.setStatus(novo);
        if (novo == StatusChamado.FECHADO) {
            chamado.setDataFechamento(LocalDateTime.now());
        }
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ComentarioResponse comentar(Long chamadoId, ComentarioRequest dto) {
        Chamado chamado = buscarEntity(chamadoId);
        Comentario salvo = comentarioRepository.save(
            new Comentario(dto.texto(), dto.autor(), chamado));
        return ComentarioResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public List<ComentarioResponse> listarComentarios(Long chamadoId) {
        buscarEntity(chamadoId); 
        return comentarioRepository.findByChamadoIdOrderByDataAsc(chamadoId).stream()
            .map(ComentarioResponse::de)
            .toList();
    }

    private Specification<Chamado> filtrar(
            StatusChamado status, Prioridade prioridade, Long clienteId) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (status != null) {
                predicados.add(cb.equal(root.get("status"), status));
            }
            if (prioridade != null) {
                predicados.add(cb.equal(root.get("prioridade"), prioridade));
            }
            if (clienteId != null) {
                predicados.add(cb.equal(root.get("cliente").get("id"), clienteId));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private void garantirNaoFechado(Chamado chamado) {
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new ChamadoFechadoException(
                "Chamado " + chamado.getId() + " está FECHADO e não pode ser alterado");
        }
    }

    private Chamado buscarEntity(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Chamado " + id + " não encontrado"));
    }

    private Cliente buscarCliente(Long id) {
        return clienteRepository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Cliente " + id + " não encontrado"));
    }

    private Categoria buscarCategoria(Long id) {
        return categoriaRepository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Categoria " + id + " não encontrada"));
    }
}
