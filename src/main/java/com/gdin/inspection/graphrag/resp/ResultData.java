package com.gdin.inspection.graphrag.resp;

import org.springframework.http.HttpStatus;

public class ResultData<T> {

    private int code;

    private String message;

    private T data;

    private long timestamp = System.currentTimeMillis();

    public ResultData() {
    }

    public static ResultData success() {
        ResultData resultData = new ResultData();
        resultData.setCode(HttpStatus.OK.value());
        resultData.setMessage(HttpStatus.OK.getReasonPhrase());
        return resultData;
    }

    public static <T> ResultData<T> success(T data) {
        ResultData<T> resultData = new ResultData();
        resultData.setCode(HttpStatus.OK.value());
        resultData.setMessage(HttpStatus.OK.getReasonPhrase());
        resultData.setData(data);
        return resultData;
    }

    public static <T> ResultData<T> fail(int code, String message) {
        ResultData<T> resultData = new ResultData();
        resultData.setCode(code);
        resultData.setMessage(message);
        return resultData;
    }

    public static ResultData<String> fail(int code, String message, String data) {
        ResultData<String> resultData = new ResultData();
        resultData.setCode(code);
        resultData.setMessage(message);
        resultData.setData(data);
        return resultData;
    }

    public static ResultData<String> fail(int code, String message, Throwable e) {
        ResultData<String> resultData = new ResultData();
        resultData.setCode(code);
        resultData.setMessage(message);
        resultData.setData(e.getMessage());
        return resultData;
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    public T getData() {
        return this.data;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public ResultData<T> setCode(final int code) {
        this.code = code;
        return this;
    }

    public ResultData<T> setMessage(final String message) {
        this.message = message;
        return this;
    }

    public ResultData<T> setData(final T data) {
        this.data = data;
        return this;
    }

    public ResultData<T> setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ResultData)) {
            return false;
        } else {
            ResultData<?> other = (ResultData)o;
            if (!other.canEqual(this)) {
                return false;
            } else if (this.getCode() != other.getCode()) {
                return false;
            } else if (this.getTimestamp() != other.getTimestamp()) {
                return false;
            } else {
                label40: {
                    Object this$message = this.getMessage();
                    Object other$message = other.getMessage();
                    if (this$message == null) {
                        if (other$message == null) {
                            break label40;
                        }
                    } else if (this$message.equals(other$message)) {
                        break label40;
                    }

                    return false;
                }

                Object this$data = this.getData();
                Object other$data = other.getData();
                if (this$data == null) {
                    if (other$data != null) {
                        return false;
                    }
                } else if (!this$data.equals(other$data)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ResultData;
    }

    public int hashCode() {
        boolean PRIME = true;
        int result = 1;
        result = result * 59 + this.getCode();
        long $timestamp = this.getTimestamp();
        result = result * 59 + (int)($timestamp >>> 32 ^ $timestamp);
        Object $message = this.getMessage();
        result = result * 59 + ($message == null ? 43 : $message.hashCode());
        Object $data = this.getData();
        result = result * 59 + ($data == null ? 43 : $data.hashCode());
        return result;
    }

    public String toString() {
        int var10000 = this.getCode();
        return "ResultData(code=" + var10000 + ", message=" + this.getMessage() + ", data=" + String.valueOf(this.getData()) + ", timestamp=" + this.getTimestamp() + ")";
    }
}
