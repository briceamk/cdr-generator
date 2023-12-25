package cm.amk.crdgenerator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FileStorageException extends RuntimeException{
    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(Throwable cause) {
        super(cause);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
