package fr.croustyquizz.soiree;

import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * QR code en SVG autonome, sans script ni ressource externe : un seul tracé
 * pour les modules sombres, sur un fond clair qui garde la marge de lecture.
 */
final class QrCodeSvg {

	/** Marge blanche autour du code, en modules, exigée par la norme pour la lecture. */
	private static final int MARGE = 4;

	private QrCodeSvg() {
	}

	static String de(String contenu) {
		BitMatrix modules;
		try {
			modules = new QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, 0, 0,
					Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
		}
		catch (WriterException e) {
			throw new IllegalArgumentException("Contenu impossible à encoder en QR code", e);
		}
		int cote = modules.getWidth() + 2 * MARGE;
		StringBuilder trace = new StringBuilder();
		for (int y = 0; y < modules.getHeight(); y++) {
			for (int x = 0; x < modules.getWidth(); x++) {
				if (modules.get(x, y)) {
					trace.append('M').append(x + MARGE).append(' ').append(y + MARGE).append("h1v1h-1z");
				}
			}
		}
		return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + cote + " " + cote
				+ "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code pour rejoindre la Soirée\">"
				+ "<rect width=\"" + cote + "\" height=\"" + cote + "\" fill=\"#fff\"/>"
				+ "<path fill=\"#000\" d=\"" + trace + "\"/></svg>";
	}

}
