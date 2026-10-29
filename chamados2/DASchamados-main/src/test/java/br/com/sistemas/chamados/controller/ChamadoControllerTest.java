package br.com.sistemas.chamados.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.sistemas.chamados.dto.AlterarStatusRequest;
import br.com.sistemas.chamados.dto.AtribuirTecnicoRequest;
import br.com.sistemas.chamados.dto.ChamadoRequest;
import br.com.sistemas.chamados.dto.ChamadoResponse;
import br.com.sistemas.chamados.dto.PaginaResponse;
import br.com.sistemas.chamados.entity.Prioridade;
import br.com.sistemas.chamados.entity.StatusChamado;
import br.com.sistemas.chamados.exception.ChamadoFechadoException;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.service.ChamadoService;

@WebMvcTest(ChamadoController.class)
class ChamadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChamadoService service;

    private ChamadoResponse respostaAberta() {
        return new ChamadoResponse(1L, "PC não liga", "Sem imagem",
            LocalDateTime.now(), null, Prioridade.ALTA, StatusChamado.ABERTO,
            1L, "Ana Souza", 1L, "Hardware", null, null);
    }

    @Test
    void criarChamadoRetorna201() throws Exception {
        when(service.criar(any(ChamadoRequest.class))).thenReturn(respostaAberta());

        mockMvc.perform(post("/chamados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"PC não liga\",\"descricao\":\"Sem imagem\","
                    + "\"prioridade\":\"ALTA\",\"clienteId\":1,\"categoriaId\":1}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/chamados/1"))
            .andExpect(jsonPath("$.status").value("ABERTO"));
    }

    @Test
    void criarSemTituloRetorna400() throws Exception {
        mockMvc.perform(post("/chamados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"\",\"descricao\":\"Sem imagem\","
                    + "\"prioridade\":\"ALTA\",\"clienteId\":1,\"categoriaId\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erro").value("Dados inválidos"));
    }

    @Test
    void prioridadeDesconhecidaRetorna400() throws Exception {
        mockMvc.perform(post("/chamados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"T\",\"descricao\":\"D\","
                    + "\"prioridade\":\"URGENTE\",\"clienteId\":1,\"categoriaId\":1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void buscarInexistenteRetorna404() throws Exception {
        when(service.buscar(99999L))
            .thenThrow(new RecursoNaoEncontradoException("Chamado 99999 não encontrado"));

        mockMvc.perform(get("/chamados/99999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void atribuirEmChamadoFechadoRetorna409() throws Exception {
        when(service.atribuir(any(Long.class), any(AtribuirTecnicoRequest.class)))
            .thenThrow(new ChamadoFechadoException("Chamado 1 está FECHADO e não pode ser alterado"));

        mockMvc.perform(patch("/chamados/1/atribuir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tecnicoId\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void transicaoInvalidaRetorna409() throws Exception {
        when(service.alterarStatus(any(Long.class), any(AlterarStatusRequest.class)))
            .thenThrow(new RegraNegocioException("Transição de status inválida: ABERTO -> AGUARDANDO_CLIENTE"));

        mockMvc.perform(patch("/chamados/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"AGUARDANDO_CLIENTE\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void listarComFiltrosRetornaPagina() throws Exception {
        when(service.listar(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PaginaResponse<>(List.of(respostaAberta()), 0, 10, 1, 1));

        mockMvc.perform(get("/chamados")
                .param("status", "ABERTO")
                .param("prioridade", "ALTA")
                .param("clienteId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.conteudo[0].titulo").value("PC não liga"));
    }

    @Test
    void statusInvalidoNoFiltroRetorna400() throws Exception {
        mockMvc.perform(get("/chamados").param("status", "XYZ"))
            .andExpect(status().isBadRequest());
    }
}
