package br.com.sistemas.chamados.dto;

import br.com.sistemas.chamados.entity.Prioridade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarChamadoRequest(
    @NotBlank(message = "Título é obrigatório")
    @Size(max = 120, message = "Título deve ter no máximo 120 caracteres")
    String titulo,

    @NotBlank(message = "Descrição é obrigatória")
    @Size(max = 1000, message = "Descrição deve ter no máximo 1000 caracteres")
    String descricao,

    @NotNull(message = "Prioridade é obrigatória")
    Prioridade prioridade,

    @NotNull(message = "Categoria é obrigatória")
    Long categoriaId
) { }
