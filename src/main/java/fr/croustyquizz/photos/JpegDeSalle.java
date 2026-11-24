package fr.croustyquizz.photos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import fr.croustyquizz.manche.Refuse;

record JpegDeSalle(byte[] octets, String empreinte) {

	static final int OCTETS_MAX = 1_500_000;
	static final int COTE_MAX = 2048;

	static JpegDeSalle lire(InputStream entree) throws IOException {
		byte[] octets = entree.readNBytes(OCTETS_MAX + 1);
		if (octets.length > OCTETS_MAX) {
			throw invalide("La photo dépasse 1,5 Mo.").exception();
		}
		if (!signatureJpeg(octets)) {
			throw invalide("La photo doit être un JPEG lisible.").exception();
		}
		try (var flux = new MemoryCacheImageInputStream(new ByteArrayInputStream(octets))) {
			ImageReader lecteur = ImageIO.getImageReadersByFormatName("JPEG").next();
			try {
				lecteur.setInput(flux, true, true);
				int largeur = lecteur.getWidth(0);
				int hauteur = lecteur.getHeight(0);
				if (largeur <= 0 || hauteur <= 0 || largeur > COTE_MAX || hauteur > COTE_MAX) {
					throw invalide("La photo doit faire au plus 2048 pixels de chaque côté.").exception();
				}
			} finally {
				lecteur.dispose();
			}
		} catch (IOException erreur) {
			throw invalide("La photo doit être un JPEG lisible.").exception(erreur);
		}
		return new JpegDeSalle(octets, empreinte(octets));
	}

	private static boolean signatureJpeg(byte[] octets) {
		int fin = octets.length;
		return fin >= 5 && octets[0] == (byte) 0xff && octets[1] == (byte) 0xd8 && octets[2] == (byte) 0xff
				&& octets[fin - 2] == (byte) 0xff && octets[fin - 1] == (byte) 0xd9;
	}

	private static String empreinte(byte[] octets) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
		} catch (NoSuchAlgorithmException erreur) {
			throw new IllegalStateException("SHA-256 indisponible.", erreur);
		}
	}

	static Refuse invalide(String detail) {
		return new Refuse(422, "DONNEES_INVALIDES", detail);
	}
}
