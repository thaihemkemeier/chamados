package br.com.sistemas.chamados.exception;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(RecursoNaoEncontradoException ex) {
        return montar(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> regraNegocio(RegraNegocioException ex) {
        return montar(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ChamadoFechadoException.class)
    public ResponseEntity<ErroResposta> chamadoFechado(ChamadoFechadoException ex) {
        return montar(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException ex) {
        return montar(HttpStatus.CONFLICT,
            "Operação viola a integridade dos dados (registro em uso ou valor duplicado)",
            List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
        return montar(HttpStatus.BAD_REQUEST, "Dados inválidos", detalhes);
    }

    /** JSON malformado ou valor de enum desconhecido no corpo (ex.: prioridade "URGENTE"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> jsonInvalido(HttpMessageNotReadableException ex) {
        return montar(HttpStatus.BAD_REQUEST,
            "JSON inválido ou valor não reconhecido", List.of());
    }

    /** Parâmetro de URL com valor inválido (ex.: ?status=XYZ). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return montar(HttpStatus.BAD_REQUEST,
            "Valor inválido para o parâmetro '" + ex.getName() + "'", List.of());
    }

    private ResponseEntity<ErroResposta> montar(
            HttpStatus status, String erro, List<String> detalhes) {
        return ResponseEntity.status(status)
            .body(new ErroResposta(LocalDateTime.now(), status.value(), erro, detalhes));
    }
}
