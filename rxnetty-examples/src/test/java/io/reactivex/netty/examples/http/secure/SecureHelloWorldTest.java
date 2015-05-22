/*
 * Copyright 2015 Netflix, Inc.
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

package io.reactivex.netty.examples.http.secure;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.reactivex.netty.examples.ExamplesEnvironment;
import io.reactivex.netty.protocol.http.internal.HttpMessageFormatter;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Queue;

import static io.reactivex.netty.examples.ExamplesTestUtil.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class SecureHelloWorldTest extends ExamplesEnvironment {

    @Test(timeout = 60000)
    public void testHelloWorld() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Queue<String> output = setupClientLogger(SecureHelloWorldClient.class);

        SecureHelloWorldClient.main(null);

        HttpResponse expectedHeader = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        expectedHeader.headers().add(Names.TRANSFER_ENCODING, Values.CHUNKED);
        String expectedHeaderString = HttpMessageFormatter.formatResponse(expectedHeader.protocolVersion(),
                                                                          expectedHeader.status(),
                                                                          expectedHeader.headers().iterator());

        assertThat("Unexpected number of messages echoed", output, hasSize(2));

        assertThat("Unexpected response.", output, contains(expectedHeaderString, "HelloWorld!"));
    }
}
