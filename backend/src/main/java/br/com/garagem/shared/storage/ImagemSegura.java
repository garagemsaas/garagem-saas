package br.com.garagem.shared.storage;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.error.ErrorCodes;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public final class ImagemSegura {
  private ImagemSegura() {}

  public record Imagem(byte[] bytes, String contentType) {}

  public static Imagem decodificar(MultipartFile file) {
    if (file.getSize() > 10 * 1024 * 1024) throw tamanhoExcedido();
    if (file.isEmpty()) throw ApiException.invalid("Envie uma foto PNG ou JPEG de até 10 MB.");
    byte[] bytes;
    String contentType;
    try {
      byte[] original = file.getBytes();
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
        var readers = ImageIO.getImageReaders(input);
        // O tipo vem do conteúdo decodificado, nunca da extensão ou do Content-Type enviados.
        if (!readers.hasNext()) throw naoSuportado();
        var reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          if (!Set.of("png", "jpeg", "jpg").contains(format)) throw naoSuportado();
          if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000)
            throw ApiException.invalid("A foto deve ter até 20 megapixels.");
          var image = reader.read(0);
          var output = new ByteArrayOutputStream();
          String target = format.equals("png") ? "png" : "jpg";
          ImageIO.write(image, target, output);
          bytes = output.toByteArray();
          contentType = target.equals("png") ? "image/png" : "image/jpeg";
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException e) {
      throw naoSuportado();
    }
    if (bytes.length > 10 * 1024 * 1024) throw tamanhoExcedido();

    return new Imagem(bytes, contentType);
  }

  private static ApiException tamanhoExcedido() {
    return new ApiException(
        HttpStatus.PAYLOAD_TOO_LARGE, ErrorCodes.PAYLOAD_TOO_LARGE, "A foto deve ter até 10 MB.");
  }

  private static ApiException naoSuportado() {
    return new ApiException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
        "Envie uma imagem PNG ou JPEG válida.");
  }
}
