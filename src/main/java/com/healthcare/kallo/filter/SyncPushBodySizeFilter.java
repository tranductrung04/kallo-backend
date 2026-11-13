package com.healthcare.kallo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.ErrorCode;

@Component
public class SyncPushBodySizeFilter extends OncePerRequestFilter {
  private static final String PUSH_PATH = "/api/sync/push";
  private static final long MAX_BODY_BYTES = 1024L * 1024L;
  private final ObjectMapper objectMapper;

  public SyncPushBodySizeFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !PUSH_PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      writeTooLarge(response);
      return;
    }
    try {
      filterChain.doFilter(new LimitedBodyRequest(request), response);
    } catch (BodyTooLargeException e) {
      writeTooLarge(response);
    }
  }

  private void writeTooLarge(HttpServletResponse response) throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(ErrorCode.SYNC_BATCH_TOO_LARGE.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(
        ApiResponse.error(ErrorCode.SYNC_BATCH_TOO_LARGE.name(), ErrorCode.SYNC_BATCH_TOO_LARGE.message())));
  }

  private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
    private LimitedBodyRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream delegate = super.getInputStream();
      return new ServletInputStream() {
        private long read;

        @Override
        public int read() throws IOException {
          int value = delegate.read();
          if (value >= 0) check(1);
          return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
          int count = delegate.read(bytes, offset, length);
          if (count > 0) check(count);
          return count;
        }

        @Override
        public int read(byte[] bytes) throws IOException {
          return read(bytes, 0, bytes.length);
        }

        @Override
        public boolean isFinished() {
          return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
          return delegate.isReady();
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener listener) {
          delegate.setReadListener(listener);
        }

        private void check(long count) {
          read += count;
          if (read > MAX_BODY_BYTES) throw new BodyTooLargeException();
        }
      };
    }
  }

  private static final class BodyTooLargeException extends RuntimeException {}
}
