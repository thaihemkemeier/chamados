package br.com.sistemas.chamados.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import br.com.sistemas.chamados.entity.Categoria;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.entity.Comentario;
import br.com.sistemas.chamados.entity.Prioridade;
import br.com.sistemas.chamados.entity.StatusChamado;

@DataJpaTest
class ChamadoRepositoryTest {

    @Autowired
    private ChamadoRepository chamadoRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private ComentarioRepository comentarioRepository;

    private Cliente cliente;
    private Categoria categoria;

    @BeforeEach
    void preparar() {
        cliente = clienteRepository.save(new Cliente("Ana Souza", "ana@exemplo.com", "47999999999"));
        categoria = categoriaRepository.save(new Categoria("Hardware", "Problemas de equipamento"));
    }

    @Test
    void chamadoNasceAbertoComDataDeAbertura() {
        Chamado salvo = chamadoRepository.save(
            new Chamado("PC não liga", "Sem imagem", Prioridade.ALTA, cliente, categoria));

        assertNotNull(salvo.getId());
        assertEquals(StatusChamado.ABERTO, salvo.getStatus());
        assertNotNull(salvo.getDataAbertura());
        assertEquals(null, salvo.getTecnico());
    }

    @Test
    void filtraPorStatusEPrioridade() {
        chamadoRepository.save(new Chamado("A", "desc", Prioridade.ALTA, cliente, categoria));
        chamadoRepository.save(new Chamado("B", "desc", Prioridade.BAIXA, cliente, categoria));
        Chamado emAtendimento = new Chamado("C", "desc", Prioridade.ALTA, cliente, categoria);
        emAtendimento.setStatus(StatusChamado.EM_ATENDIMENTO);
        chamadoRepository.save(emAtendimento);

        Specification<Chamado> abertosAltos = (root, query, cb) -> cb.and(
            cb.equal(root.get("status"), StatusChamado.ABERTO),
            cb.equal(root.get("prioridade"), Prioridade.ALTA));

        var pagina = chamadoRepository.findAll(abertosAltos, PageRequest.of(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("A", pagina.getContent().get(0).getTitulo());
    }

    @Test
    void listaComentariosEmOrdemCronologica() {
        Chamado chamado = chamadoRepository.save(
            new Chamado("PC não liga", "Sem imagem", Prioridade.ALTA, cliente, categoria));
        comentarioRepository.save(new Comentario("Primeiro", "Maria", chamado));
        comentarioRepository.save(new Comentario("Segundo", "Maria", chamado));

        List<Comentario> comentarios =
            comentarioRepository.findByChamadoIdOrderByDataAsc(chamado.getId());

        assertEquals(2, comentarios.size());
        assertEquals("Primeiro", comentarios.get(0).getTexto());
    }
}
