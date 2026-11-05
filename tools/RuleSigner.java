import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 给规则 JSON 打签名，生成可被 App 校验的规则包。
 * 用法：java tools/RuleSigner.java keys/rule-signing.pkcs8 samples/import-demo.json out/cx330-rules.signed.json
 *
 * 规则包格式：
 * {
 *   "payload": "<原始规则 JSON 的 Base64>",
 *   "signature": "<SHA256withRSA 签名 Base64>",
 *   "keyId": "builtin-1",
 *   "algorithm": "SHA256withRSA"
 * }
 */
public class RuleSigner {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: RuleSigner <private.pkcs8> <rules.json> <out.signed.json>");
            System.exit(2);
        }
        byte[] privateBytes = Files.readAllBytes(Paths.get(args[0]));
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

        byte[] payload = Files.readAllBytes(Paths.get(args[1]));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload);
        byte[] signed = signature.sign();

        String payloadB64 = Base64.getEncoder().encodeToString(payload);
        String signatureB64 = Base64.getEncoder().encodeToString(signed);

        String bundle = "{\n"
                + "  \"payload\": \"" + payloadB64 + "\",\n"
                + "  \"signature\": \"" + signatureB64 + "\",\n"
                + "  \"keyId\": \"builtin-1\",\n"
                + "  \"algorithm\": \"SHA256withRSA\"\n"
                + "}\n";
        Files.createDirectories(Paths.get(args[2]).getParent());
        Files.write(Paths.get(args[2]), bundle.getBytes(StandardCharsets.UTF_8));
        System.out.println("signed rules written to " + args[2]);
    }
}
