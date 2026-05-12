package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.ParserConfiguration;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// TODO See for different HL7v2 version ?
public class HL7v2DataReader {

	private static final Logger logger = LoggerFactory.getLogger(HL7v2DataReader.class);

	/**
	 * Parses a Base64 encoded HL7v2 string and returns a {@link Message}.
	 *
	 * @param content The Base64 encoded HL7v2 string to parse.
	 * @return A JSONObject parsed from the decoded JSON content.
	 * @throws InternalErrorException If there's an error while parsing or decoding the JSON content.
	 */
	public static Message parseData(String content) {
		String hl7v2Content = new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8)
			.replace("\r\n", "\r")
			.replace("\n", "\r");

		try (HapiContext context = new DefaultHapiContext()) {
			context.setValidationContext(new NoValidation());

			Parser parser = context.getGenericParser();
			Message msg = parser.parse(hl7v2Content);
			logger.info("HL7v2 parsed class = {}", msg.getClass().getName());
			return msg;
		} catch (IOException e) {
			logger.error("Error while creating context for HL7v2 parsing !", e);
			throw new InternalErrorException("Error while creating context for HL7v2 parsing !");
		} catch (HL7Exception e) {
			logger.error("Error while reading HL7v2 Data from DocumentReference !", e);
			try (HapiContext context = new DefaultHapiContext()) {
				context.setValidationContext(new NoValidation());
				context.getParserConfiguration().setAllowUnknownVersions(true);

				// Force HAPI to use generic message/segment classes
				context.setModelClassFactory(new GenericModelClassFactory());

				Parser parser = context.getPipeParser();

				// Replace MSH-12 with temporary unknown version to force generic parsing
				String genericPayload = forceUnknownVersionInMsh12(hl7v2Content, "9.9");

				Message message = parser.parse(genericPayload);

				restoreMsh12(message, getMsh12(hl7v2Content));

				return message;
			} catch (Exception e1) {
				logger.error("Error while reading HL7v2 Data as generic from DocumentReference !", e1);
				throw new InternalErrorException("Error while reading HL7v2 Data from DocumentReference !");
			}
		}
	}

	private static String forceUnknownVersionInMsh12(String hl7Payload, String forcedVersion) {
		String[] lines = hl7Payload.split("\\r?\\n|\\r", -1);

		for (int i = 0; i < lines.length; i++) {
			if (!lines[i].startsWith("MSH|")) {
				continue;
			}

			String[] fields = lines[i].split("\\|", -1);

			if (fields.length <= 11) {
				throw new IllegalArgumentException("MSH segment does not contain MSH-12");
			}

			fields[11] = forcedVersion;
			lines[i] = String.join("|", fields);
			break;
		}

		return String.join("\r", lines);
	}

	private static String getMsh12(String hl7Payload) {
		String[] lines = hl7Payload.split("\\r?\\n|\\r", -1);

		for (String line : lines) {
			if (!line.startsWith("MSH|")) {
				continue;
			}

			String[] fields = line.split("\\|", -1);

			if (fields.length <= 11) {
				throw new IllegalArgumentException("MSH segment does not contain MSH-12");
			}

			return fields[11];
		}

		throw new IllegalArgumentException("No MSH segment found");
	}

	private static void restoreMsh12(Message message, String originalVersion) throws HL7Exception {
		Terser terser = new Terser(message);
		terser.set("/MSH-12", originalVersion);
	}
}
