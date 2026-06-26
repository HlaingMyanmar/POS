package org.sspd.servicemgmt.exceptionhandler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ဒီ Exception တက်လာရင် 404 Not Found ပြမယ်လို့ သတ်မှတ်တာပါ
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException{

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
