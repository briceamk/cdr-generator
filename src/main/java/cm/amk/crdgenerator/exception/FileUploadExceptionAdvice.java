package cm.amk.crdgenerator.exception;

import cm.amk.crdgenerator.payload.ResponseMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class FileUploadExceptionAdvice extends ResponseEntityExceptionHandler {

    @ModelAttribute("contextPath")
    String getRequestServletPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getContextPath();
    }

    /*@ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseMessage> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(ResponseMessage.builder().success(false).message("File exceeded 10MB!").build());
    }*/

    @ExceptionHandler(FileStorageException.class)
    protected final ResponseEntity<ResponseMessage> handleFileStorageException(FileStorageException e,
                                                                            WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseMessage.builder().success(false).message(e.getLocalizedMessage()).build());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    protected final ResponseEntity<ResponseMessage> handleResourceNotFoundException(ResourceNotFoundException e,
                                                                                 WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseMessage.builder().success(false).message(e.getLocalizedMessage()).build());
    }
}