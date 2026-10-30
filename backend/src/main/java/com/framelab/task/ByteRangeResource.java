package com.framelab.task;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

final class ByteRangeResource extends AbstractResource {

    private final Resource delegate;
    private final long start;
    private final long count;

    ByteRangeResource(Resource delegate, long start, long count) {
        this.delegate = delegate;
        this.start = start;
        this.count = count;
    }

    @Override
    public String getDescription() {
        return "Byte range " + start + "+" + count + " of " + delegate.getDescription();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream input = delegate.getInputStream();
        try {
            long remaining = start;
            while (remaining > 0) {
                long skipped = input.skip(remaining);
                if (skipped > 0) {
                    remaining -= skipped;
                } else if (input.read() >= 0) {
                    remaining--;
                } else {
                    break;
                }
            }
            return new BoundedInputStream(input, count);
        } catch (IOException ex) {
            input.close();
            throw ex;
        }
    }

    @Override
    public long contentLength() {
        return count;
    }

    @Override
    public String getFilename() {
        return delegate.getFilename();
    }

    private static final class BoundedInputStream extends FilterInputStream {

        private long remaining;

        BoundedInputStream(InputStream input, long limit) {
            super(input);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = super.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), remaining);
        }
    }
}
