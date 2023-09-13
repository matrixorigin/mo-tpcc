package io.mo;

public class RowNotFoundException extends Exception{
    private int ErrorCode = 9999;
    
    public RowNotFoundException(String errorMessage){
        super(errorMessage);
    }
    
    public int getErrorCode(){
        return ErrorCode;
    }
    
    public String getMessage(){
        return super.getMessage();
    }
}
