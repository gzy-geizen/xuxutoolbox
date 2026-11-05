import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * 生成规则签名用的 RSA 密钥对（一次性工具）。
 * 用法：java tools/KeyGen.java keys
 * - 公钥写入 keys/rule-signing.pub.b64（Base64 SPKI，嵌进 App）
 * - 私钥写入 keys/rule-signing.pkcs8（PKCS#8 DER，不上传、不进版本库）
 */
public class KeyGen {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "keys";
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        Files.createDirectories(Paths.get(dir));
        Files.write(Paths.get(dir, "rule-signing.pub.b64"),
                Base64.getEncoder().encode(pair.getPublic().getEncoded()));
        Files.write(Paths.get(dir, "rule-signing.pkcs8"), pair.getPrivate().getEncoded());
        System.out.println("generated keys in " + dir);
    }
}
