package senada.lyrics.sources

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZlibAndQrcCryptoTest {
    @Test
    fun inflatesStoredFixedAndDynamicHuffmanStreams() {
        val stored = ZlibInflater.inflate(QqQrcCrypto.decodeHexOrNull(ZLIB_STORED_HEX)!!)
        assertEquals("Stored block placeholder!", stored.decodeToString())

        val fixed = ZlibInflater.inflate(QqQrcCrypto.decodeHexOrNull(ZLIB_FIXED_HEX)!!)
        assertEquals("Hello world! Hello world! Hello world!", fixed.decodeToString())

        val dynamic = ZlibInflater.inflate(QqQrcCrypto.decodeHexOrNull(ZLIB_DYNAMIC_HEX)!!)
        val expectedDynamic = buildString {
            for (i in 0 until 18) {
                append("[${i * 1000},900]<0,400,0>Word$i <400,500,0>夜空${i % 7} line-alpha-beta-gamma!\n")
            }
        }
        assertEquals(expectedDynamic, dynamic.decodeToString())
    }

    @Test
    fun ignoresTrailingPaddingBytesAfterAdlerTrailerAndRejectsCorruption() {
        val valid = QqQrcCrypto.decodeHexOrNull(ZLIB_FIXED_HEX)!!
        val padded = valid + byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)
        assertEquals("Hello world! Hello world! Hello world!", ZlibInflater.inflate(padded).decodeToString())

        val badChecksum = valid.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0xFF).toByte() }
        assertFailsWith<IllegalArgumentException> { ZlibInflater.inflate(badChecksum) }

        assertFailsWith<IllegalArgumentException> { ZlibInflater.inflate(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)) }
        assertFailsWith<IllegalArgumentException> { ZlibInflater.inflate(valid.copyOf(valid.size - 2)) }
    }

    @Test
    fun qqTripleDesRoundTripsMultiBlockPayload() {
        val plaintext = "0123456789ABCDEFabcdefghijklmnop".encodeToByteArray()
        val encrypted = QqQrcCrypto.tripleDesEncrypt(plaintext)
        val decrypted = QqQrcCrypto.tripleDesDecrypt(encrypted)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun decryptsQqQrcHexAndExtractsLyricContentWithUnescapedQuotes() {
        val decrypted = assertNotNull(QqQrcCrypto.decryptQrc(QRC_ENCRYPTED_HEX))
        assertTrue(decrypted.contains("<QrcInfos>"))

        val content = QqQrcCrypto.extractLyricContent(decrypted)
        assertTrue(content.contains("Author \"Nick\" Name"))
        assertTrue(content.contains("[2000,2500]Hello (2000,600)world (2600,700)(ooh (3300,500)yeah)(3800,700)"))
        assertTrue(content.contains("[7500,1800]女：(7500,200)星(7700,700)光(8400,900)"))
    }

    @Test
    fun extractLyricContentUnescapesXmlEntities() {
        val xml = """<QrcInfos><LyricInfo><Lyric_1 LyricType="1" LyricContent="[1000,500]Tom &amp; Jerry&#39;s &quot;Song&quot;(1000,500)"/></LyricInfo></QrcInfos>"""
        assertEquals(
            """[1000,500]Tom & Jerry's "Song"(1000,500)""",
            QqQrcCrypto.extractLyricContent(xml),
        )
    }

    @Test
    fun decryptQrcReturnsNullOnInvalidInput() {
        assertNull(QqQrcCrypto.decryptQrc(""))
        assertNull(QqQrcCrypto.decryptQrc("not-hex!"))
        assertNull(QqQrcCrypto.decryptQrc("DEADBEEF00112233"))
    }

    @Test
    fun decryptsKuGouKrcAndStripsUtf8Bom() {
        val krc = assertNotNull(KuGouSource.decryptKrc(KRC_BASE64))
        assertTrue(krc.startsWith("[ti:Morning Light]"))
        assertTrue(krc.contains("[2000,2500]<0,600,0>Hello <600,700,0>world <1300,500,0>(ooh <1800,700,0>yeah)"))
        assertNull(KuGouSource.decryptKrc("not-valid-krc"))
    }

    internal companion object {
        const val ZLIB_STORED_HEX = "7801011900E6FF53746F72656420626C6F636B20706C616365686F6C646572217B3C0961"
        const val ZLIB_FIXED_HEX = "7801F348CDC9C95728CF2FCA495154C0C9010006370D58"
        const val ZLIB_DYNAMIC_HEX =
            "78DA95D33B0AC2401485E1DE55C43E817BE6950482DBB00816230615120DE2722C6DDC82FB11B721B19D33C52DE7165F" +
                "F39FE9A56C45769D944EA494CDF67A3B48D12D0FFF3F7C9E8FEFEB2DC578BE0C551CE753ACF6C33D56C7384D71BDEA21" +
                "4208A404B284A184490993252C256C4AD82CE128E152C265094F099F123E4B044A84940859A2A644AD89DA50A2D1446D" +
                "29D16AA22ED362DB124D5664060A4D58F089C268D2828F145613177CA670AA3FCB870AAF090C3E550455623E56D48AC4" +
                "3F1FCD7A82"
        const val QRC_ENCRYPTED_HEX =
            "BF7A5AEF4105F26509F70C45697F7C588B1D5B1FCB7970C48C6D0F6D84F27491F122F97C796F069485F28ECA3D59B518" +
                "A983F7A8F42BD28788634AEF507EF8AD552556BA961951800BF791850A52E95B82518D545D376D04BFACB93A94E925EB" +
                "C80FB74F845CAC3D8D86FCE0112A5F27561980D92176D08A4F6D56811052F2AE69B4F05C2B0E0090226738E8986DF3D4" +
                "946FB13666CACCB05ACC82551437CCF45B84121710F184805CD0B802B0F27DC81795361D61EEEC33CC58EC60B3CB906D" +
                "2922A3DCA786E2AA456860E36A9A1F83CCA3DE737F48FBFEEF7B887E0FFAB65CAEEBF7158DDC946B26458563DA596299" +
                "B62E224C60EE587E4E1F4D200E596CD07CCE556A438D96AB47F6A6D460EA735CE365266FAD8FEE9147D3FF71F84250C7" +
                "281686D61113E6A4431CDBFF33545504D95C83227168E9D8267D09A7E2166231DE412A370FCCE7ABFC12DB28E602F52B" +
                "FC526E934B6E1D816A0A2FE91F33DA0A3D62CDCE766F99E752227E69EFFB9C62"
        const val QQ_ENCRYPTED_LRC_HEX =
            "C9686507758351C0711CF0B4CD95DCC80B0D9546C021949A5A66DB242251A3B7BBDD79144B578C8CD7EC6E0439A7F7EB" +
                "D06FA59EAB30225EA1010A6680B60B32"
        const val KRC_BASE64 =
            "a3JjMTjbGsglTQAO96NeU5+MCDf6xS4RJHQ9awZCU7xbmQQH/sUCJhdURQ9xCRyA9meqo2DRSq0e9aSHkRbn644VuqkAgKF5+" +
                "OVyg9Q+sKCfAjtp2YZ0egw8oyRTYMFJIU55gjuYju47MZbc0PMtZoXgnOL45ew3lL84ZpsXYzNn61eQSnFB+Wdq1HvuHhZoD" +
                "xS+cS7iJwkgtPbK8DNtZKu4DO9mENVehgbcohdWqMqdZO0FRjYhvVPe6mnIVTJa6pqvox0bsJF+QTWhcAIPcHTLyHgigNJur" +
                "V9YSl1CcfUOPAcCZ0dNBjrU"
    }
}
