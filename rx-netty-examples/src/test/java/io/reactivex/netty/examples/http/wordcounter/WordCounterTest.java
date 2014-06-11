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

package io.reactivex.netty.examples.http.wordcounter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class WordCounterTest {

    private static final int PORT = 8097;
    private static final String TEXT_FILE = WordCounterTest.class.getClassLoader().getResource("document.txt").getFile();
    private static final int EXPECTED_N_OF_WORDS = 64;

    private Thread server;

    @Before
    public void startServer() {
        server = new Thread(new Runnable() {
            @Override
            public void run() {
                WordCounterServer.main(new String[]{Integer.toString(PORT)});
            }
        });
        server.start();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.interrupt();
        }
    }

    @Test
    public void testWordCounter() throws Exception {
        WordCounterClient client = new WordCounterClient(PORT, TEXT_FILE);
        int amount = client.countWords();
        Assert.assertEquals(EXPECTED_N_OF_WORDS, amount);
    }
}
