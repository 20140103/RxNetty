/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.Charset;

/**
 * @author Nitesh Kant
 */
public class StringTransformer implements ContentTransformer<String> {

    private final Charset charset;

    public StringTransformer() {
        this(Charset.defaultCharset());
    }

    public StringTransformer(Charset charset) {
        this.charset = charset;
    }

    @Override
    public ByteBuf transform(String toTransform, ByteBufAllocator byteBufAllocator) {
        byte[] contentAsBytes = toTransform.getBytes(charset);
        return byteBufAllocator.buffer(contentAsBytes.length).writeBytes(contentAsBytes);
    }
}
