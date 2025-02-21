package com.github.yybmion.wrapper.client;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.yybmion.wrapper.exception.PublicDataException;

public class RetryInterceptor implements Interceptor {
    private final int maxRetries;
    private static final Logger logger = LoggerFactory.getLogger(RetryInterceptor.class);

    public RetryInterceptor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        int tryCount = 0;
        IOException lastException = null;

        while (tryCount < maxRetries) {
            try {
                Response response = chain.proceed(request);
                if (response.isSuccessful()) {
                    return response;
                }

                // 특정 에러 코드의 경우 재시도하지 않음
                if (response.code() == 400 || response.code() == 404) {
                    return response;
                }

                if (response.body() != null) {
                    response.close();
                }
            } catch (IOException e) {
                lastException = e;
                logger.warn("Attempt {} failed: {}", tryCount + 1, e.getMessage());
            }

            tryCount++;
            if (tryCount < maxRetries) {
                long delayMillis = calculateDelay(tryCount);
                logger.info("Retrying in {} ms", delayMillis);
                sleep(delayMillis);
            }
        }

        throw new PublicDataException(
                "Failed after " + maxRetries + " retries",
                PublicDataException.SERVICE_TIMEOUT
        );
    }

    // 지수 백오프로 재시도 대기 시간 계산
    private long calculateDelay(int retryCount) {
        return (long) (Math.pow(2, retryCount - 1) * 1000);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
