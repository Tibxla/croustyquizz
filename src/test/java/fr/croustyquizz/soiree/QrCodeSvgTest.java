package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;

class QrCodeSvgTest {

	private static final Pattern COTE = Pattern.compile("viewBox=\"0 0 (\\d+) \\1\"");
	private static final Pattern MODULE = Pattern.compile("M(\\d+) (\\d+)h1v1h-1z");

	@Test
	void leQrCodeSeRelitEnAdresseDeLaManette() throws Exception {
		String adresse = "https://croustyquizz.example/manette.html#code=K7PQ2M";

		assertThat(relire(QrCodeSvg.de(adresse))).isEqualTo(adresse);
	}

	@Test
	void leSvgEstAutonome() {
		String svg = QrCodeSvg.de("http://localhost/manette.html#code=K7PQ2M");

		assertThat(svg).startsWith("<svg ").doesNotContain("<script", "href", "url(");
	}

	/** Reconstruit la matrice à partir du tracé, sans la marge, puis la décode. */
	private static String relire(String svg) throws Exception {
		Matcher cote = COTE.matcher(svg);
		assertThat(cote.find()).isTrue();
		int marge = 4;
		int taille = Integer.parseInt(cote.group(1)) - 2 * marge;
		BitMatrix modules = new BitMatrix(taille);
		Matcher module = MODULE.matcher(svg);
		while (module.find()) {
			modules.set(Integer.parseInt(module.group(1)) - marge, Integer.parseInt(module.group(2)) - marge);
		}
		return new Decoder().decode(modules).getText();
	}

}
