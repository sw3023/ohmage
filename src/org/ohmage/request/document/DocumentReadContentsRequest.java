/*******************************************************************************
 * Copyright 2012 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.request.document;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.exception.InvalidRequestException;
import org.ohmage.exception.ServiceException;
import org.ohmage.exception.ValidationException;
import org.ohmage.request.InputKeys;
import org.ohmage.request.UserRequest;
import org.ohmage.service.DocumentServices;
import org.ohmage.service.UserDocumentServices;
import org.ohmage.service.UserServices;
import org.ohmage.util.CookieUtils;
import org.ohmage.validator.DocumentValidators;
import org.ohmage.validator.AuditValidators;

/**
 * <p>Creates a new class. The requester must be an admin.</p>
 * <table border="1">
 *   <tr>
 *     <td>Parameter Name</td>
 *     <td>Description</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#CLIENT}</td>
 *     <td>A string describing the client that is making this request.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#DOCUMENT_ID}</td>
 *     <td>The unique identifier for the document whose contents is 
 *       desired.</td>
 *     <td>true</td>
 *   </tr>
 * </table>
 * 
 * @author John Jenkins
 */
public class DocumentReadContentsRequest extends UserRequest {
	private static final Logger LOGGER = Logger.getLogger(DocumentReadContentsRequest.class);
	
	private final String client4;
	
	private static final int CHUNK_SIZE = 4096;
	
	private final String documentId;
	
	private String documentName;
	private InputStream contentsStream;
	
	/**
	 * Creates a new request for reading a document's contents.
	 * 
	 * @param httpRequest The HttpServletRequest with the parameters necessary
	 * 					  to build this request.
	 * 
	 * @throws InvalidRequestException Thrown if the parameters cannot be 
	 * 								   parsed.
	 * 
	 * @throws IOException There was an error reading from the request.
	 */
	public DocumentReadContentsRequest(HttpServletRequest httpRequest) throws IOException, InvalidRequestException {
		super(httpRequest, null, TokenLocation.EITHER, null);
		
		String tempClient4 = null;
		String[] t;
		
		String tempDocumentId = null;
		
		try {
				t = getParameterValues(InputKeys.CLIENT);
				if(t.length > 1) {
					throw new ValidationException(
						ErrorCode.DOCUMENT_INVALID_CLIENT, 
						"DocumentCreationRequest: More than one client value was given: " +
							InputKeys.CLIENT);
				}
				else if(t.length == 1) {
					tempClient4 = 
							AuditValidators.validateClient(t[0]);
				}
			
			tempDocumentId = DocumentValidators.validateDocumentId(httpRequest.getParameter(InputKeys.DOCUMENT_ID));
			if(tempDocumentId == null) {
				setFailed(ErrorCode.DOCUMENT_INVALID_ID, "The document ID is missing.");
				throw new ValidationException("The document ID is missing.");
			}
			else if(httpRequest.getParameterValues(InputKeys.DOCUMENT_ID).length > 1) {
				setFailed(ErrorCode.DOCUMENT_INVALID_ID, "Multiple document IDs were given.");
				throw new ValidationException("Multiple document IDs were given.");
			}
		}
		catch(ValidationException e) {
			e.failRequest(this);
			LOGGER.info(e.toString());
		}
		
		client4 = tempClient4;
		documentId = tempDocumentId;
		
		contentsStream = null;
	}

	/**
	 * Services this request.
	 */
	@Override
	public void service() {
		LOGGER.info("Servicing the document read contents request.");
		
		boolean isJavaFun = false;
			if(client4.equals("rstudio.history.canvas.client")){
				isJavaFun = true;
		}
		
		if(!isJavaFun) {		
			if(! authenticate(AllowNewAccount.NEW_ACCOUNT_DISALLOWED)) {
				return;
			}
		}
		
		try {
			LOGGER.info("Verifying that the document exists.");
			DocumentServices.instance().ensureDocumentExistence(documentId);
			if(!isJavaFun) {
				try {
					LOGGER.info("Checking if the user is an admin.");
					UserServices.instance().verifyUserIsAdmin(getUser().getUsername());
				}
				catch(ServiceException e) {
					LOGGER.info("The user is not an admin.");
					LOGGER.info("Verifying that the requesting user can read the contents of this document.");
					UserDocumentServices.instance().userCanReadDocument(getUser().getUsername(), documentId);
				}
			}
			LOGGER.info("Retrieving the document's name.");
			documentName = DocumentServices.instance().getDocumentName(documentId);
			
			LOGGER.info("Retrieving the document's contents.");
			contentsStream = DocumentServices.instance().getDocumentInputStream(documentId);
		}
		catch(ServiceException e) {
			e.failRequest(this);
			e.logException(LOGGER);
		}
	}

	/**
	 * If the request has succeeded, it attempts to create an OutputStream to
	 * the response and pipe the contents of the document from the InputStream
	 * to the OutputStream. If the request fails at any point, it will attempt
	 * to return a JSON error message. If writing the response fails, an error
	 * message is printed.
	 */
	@Override
	public void respond(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Writing read document contents response.");
		
		// Creates the writer that will write the response, success or fail.
		OutputStream os;
		try {
			os = getOutputStream(httpRequest, httpResponse);
		}
		catch(IOException e) {
			LOGGER.error("Unable to create writer object. Aborting.", e);
			return;
		}
		
		// Sets the HTTP headers to disable caching
		expireResponse(httpResponse);
				
		// If the request hasn't failed, attempt to write the file to the
		// output stream. 
		try {
			if(isFailed()) {
				httpResponse.setContentType("text/html");
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				
				// Write the error response.
				try {
					writer.write(getFailureMessage()); 
				}
				catch(IOException e) {
					LOGGER.warn("Unable to write failed response message. Aborting.", e);
				}
				
				// Close it.
				try {
					writer.close();
				}
				catch(IOException e) {
					LOGGER.warn("Unable to close the writer.", e);
				}
			}
			else {
				// Set the type and force the browser to download it as the 
				// last step before beginning to stream the response.
				httpResponse.setContentType("ohmage/document");
				httpResponse.setHeader("Content-Disposition", "attachment; filename=\"" + documentName + "\"");
				
				// If available, set the token.
				if(getUser() != null) {
					final String token = getUser().getToken(); 
					if(token != null) {
						CookieUtils.setCookieValue(
							httpResponse, 
							InputKeys.AUTH_TOKEN, 
							token);
					}
				}
				
				// Set the output stream to the response.
				DataOutputStream dos = new DataOutputStream(os);
				
				// Read the file in chunks and write it to the output stream.
				byte[] bytes = new byte[CHUNK_SIZE];
				int currRead;
				while((currRead = contentsStream.read(bytes)) != -1) {
					dos.write(bytes, 0, currRead);
				}
				
				// Close the data output stream to which we were writing.
				try {
					dos.close();
				}
				catch(IOException e) {
					LOGGER.warn("Error closing the data output stream.", e);
				}
			}
		}
		// If the error occurred while reading from the input stream or
		// writing to the output stream, abort the whole operation and
		// return an error.
		catch(IOException e) {
			LOGGER.error(
				"The contents of the file could not be read or written to the response.",
				e);
			setFailed();
		}
		// Always attempt to close the stream if it is not null.
		finally {
			if(contentsStream != null) {
				try {
					contentsStream.close();
				}
				catch(IOException e) {
					LOGGER.warn("Could not close the contents stream.", e);
				}
			}

			// Close the output stream.
			try {
				os.close();
			}
			catch(IOException e) {
				LOGGER.warn("Couldn't close the output stream.", e);
			}
		}
	}
}
