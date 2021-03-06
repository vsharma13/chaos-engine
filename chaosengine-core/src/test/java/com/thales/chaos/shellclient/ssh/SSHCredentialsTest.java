/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.shellclient.ssh;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SSHCredentialsTest {
    //I made this key explicitly for this test, and it is not used anywhere. Don't worry.
    private static final String SAMPLE_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n" + "MIIEowIBAAKCAQEAuTVHJN0VorNNrJa8yPptpIA7QV+fWuzcAEPlf3rNyVUovF1o\n" + "z0ATPggupBR4Uvh0AmlTASUuQAMevBo4IjWLJENMAQnmRN/5s1peaFt1RwimvBS4\n" + "pttoCsNCvq019XijlYD0dHeW43vFgmUC2RPrAOUhcteGHgjLj7znHut852FtPsV4\n" + "nJN6tFpxuWFb9g6QtKYsSeedwwbEUbHajv2eyj1Pb1quSJVHjVmbDMbgShDnj3gB\n" + "VFfkE7amTagGnsdsqKOKQ4n+ksAt86UUaqJEgeNDAoAzIy0OEInp9m3raYIBHHDr\n" + "RCSvwcmU4tRvLe+InkBzJpjsO7XKa7+Tk/LLiQIDAQABAoIBAAjW+iZQRxAU7sgm\n" + "CayuBz2qwSlFnx1/4KBCnVmQSvIpFmCnNLFvpXt4eIFsWLHdGexjioqrc6GqhHUF\n" + "61f2pkV80Mvb5Rm0dv6QeaERfG9/gjXH52hPYI/i8fSX39Nvhp9EIGbOrmz9I1gt\n" + "6gziXn0UcpxAnS4hzVV29rELHFMOxnBoOaYXsMKOUNZt0b+YbPmVjR8J45fWmOxP\n" + "xMpNdYH0/EvOcNLuGCnRLSsRxwz0Pq/A7KAyIbuOHhzVv1QQyGMf15K+K9BtWAk6\n" + "VemY8KWwrbIWvHmSpT8joMUir5LTjMtxMfXcV56vujz6NqenPJ4msomiOAXKqo9a\n" + "0zPcQGECgYEA9Y8dM3QXcbmIo90hn2gDKC8s50ibO4erNCu4qL58X66zkBFvCrY5\n" + "EDQkWJWjJOZieCQzp4ViYjsWJ1yCGyMVf/cvtOL8RAtgxw97dLbY1jnNGJldYZhy\n" + "KZPqxSNcvWKaDtKMLEME0ltdSZu6iuGZ3qTeMQ1Ie1uqEmVIwHgaL6cCgYEAwRU/\n" + "/+TwBDZ5ytplCq3yzyZUteXhcnVzEjOqiWEENVWa6dwPRS2evuKXjWl5+6goknKA\n" + "OkomdElAxMGxCXPUQsxHF0sh0OeF/kBJHw2UNr3UalkPS0vypC1T226cQJAk3hMP\n" + "hCfkfY+5egiJjbiJsWPVbz+N2aGgUGmK6XosEU8CgYB2OLCWVQ30cp6WRaAqXqkm\n" + "b4/uycXyox6Jv4Lnus2mQzWv5rTPM2vFoVTeUSx6V3CQ5tz3igATyt6flKoO5pUz\n" + "Ro5xR9ar685sB8goKN+8Q5e6gBu1IO/VlKEyyBOeGhqRiEx0AtAPdm0zQwZMVtD9\n" + "n+JZO0vqF/eJY7ni7++rgQKBgBnMshmKSLv3X3zIg9nSdnBRid4oUmCN6wtSlgX3\n" + "Ta2mu6Vl/zBaMS0GcAO4RVFoHcu7uwCO6eZ950ajLvm5XXJzg0hMhL86QIBBrMOW\n" + "d+h8owZ20gFYq5peaCcY94309MgrudYJ0pEohDsXKvIh51wpn9pWnqim3RoBkEs0\n" + "RPEnAoGBAPOsCu11175ZnbIZfHpsVbjgrQ7ub5K2w8F0hNR3ckuyZ9VQw64yJRqH\n" + "Oyjy+o53Tdg/I8l+DeBpVt4lhFsMosNdgTBEpu3xz04M1Rejx+LGj9UxtGcdqwO0\n" + "QbPhZeUTW9xFpR9iU03JQ2EbB+qqFwHjroGhyY5bEMxhUHy8Ujux\n" + "-----END RSA PRIVATE KEY-----\n";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void privateKeyFromString () {
        RSAPrivateKey privateKey = (RSAPrivateKey) SSHCredentials.privateKeyFromString(SAMPLE_PRIVATE_KEY);
        assertEquals(new BigInteger("1115915621989129703607326876233416494044323293281722471296201505083695907563571247888481525465577555825094310512065426072326946617950609385239828498094659200622173175730677881621692979928184043298869700295977878717103751682640487389871981918403900766112266160451474035809296089797420570231415106218256044580719708048221163921738902175941039537356380633258142060580214530377043691694805417490607908926231289906663076940330032866266795395044155867354860587236862239533972914932636898083501953597002687165694010925152617189706088340387124228604620362901732658928270407159546426388730318686138178806604671768786024284257"), privateKey
                .getPrivateExponent());
        assertEquals("RSA", privateKey.getAlgorithm());
        assertEquals("PKCS#8", privateKey.getFormat());
        assertEquals(new BigInteger("23380358733472376401954405846454417126017524191753275447998452058398394723143788002834850938119423361927495469318808129316525287883832828414470153542081099754212072703919896524245809726839321497978907783982577441650520643869951925214207186376737993768765852735776295998987799820029907900017983317207751404631582803167508217819384030250972559853642009287221994162500955300709133653125663747193505532022190410183278970529788290860839607895593518848023013257070224904576680848602251463145247011130423526475655698449845934737351568575370060704501001863767293543130042725837589577444587419852188443978827031325835103751049"), privateKey
                .getModulus());
    }

    @Test
    public void publicKeyFromPrivateKey () {
        PrivateKey privateKey = SSHCredentials.privateKeyFromString(SAMPLE_PRIVATE_KEY);
        RSAPublicKey publicKey = (RSAPublicKey) SSHCredentials.publicKeyFromPrivateKey(privateKey, null);
        assertEquals(BigInteger.valueOf(65537L), publicKey.getPublicExponent());
        assertEquals(new BigInteger(
                        "23380358733472376401954405846454417126017524191753275447998452058398394723143788002834850938119423361927495469318808129316525287883832828414470153542081099754212072703919896524245809726839321497978907783982577441650520643869951925214207186376737993768765852735776295998987799820029907900017983317207751404631582803167508217819384030250972559853642009287221994162500955300709133653125663747193505532022190410183278970529788290860839607895593518848023013257070224904576680848602251463145247011130423526475655698449845934737351568575370060704501001863767293543130042725837589577444587419852188443978827031325835103751049"),
                publicKey.getModulus());
        assertEquals("RSA", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
    }

    @Test
    public void SSHCredentialsHasJsonIgnoreType () {
        assertNotNull(
                "SSH Credentials should have JsonIgnoreType annotation to prevent iteration and keys being exposed by Jackson/FasterXML",
                SSHCredentials.class.getDeclaredAnnotation(JsonIgnoreType.class));
    }
}