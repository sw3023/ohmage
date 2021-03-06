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
package org.ohmage.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.domain.Audio;
import org.ohmage.domain.IMedia;
import org.ohmage.domain.Image;
import org.ohmage.domain.Video;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.domain.campaign.PromptResponse;
import org.ohmage.domain.campaign.RepeatableSetResponse;
import org.ohmage.domain.campaign.Response;
import org.ohmage.domain.campaign.SurveyResponse;
import org.ohmage.domain.campaign.SurveyResponse.ColumnKey;
import org.ohmage.domain.campaign.SurveyResponse.SortParameter;
import org.ohmage.domain.campaign.prompt.MediaPrompt;
import org.ohmage.domain.campaign.response.AudioPromptResponse;
import org.ohmage.domain.campaign.response.FilePromptResponse;
import org.ohmage.domain.campaign.response.MediaPromptResponse;
import org.ohmage.domain.campaign.response.PhotoPromptResponse;
import org.ohmage.domain.campaign.response.VideoPromptResponse;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.DomainException;
import org.ohmage.exception.ServiceException;
import org.ohmage.query.IImageQueries;
import org.ohmage.query.ISurveyResponseImageQueries;
import org.ohmage.query.ISurveyResponseQueries;
import org.ohmage.query.ISurveyUploadQuery;

/**
 * This class is responsible for creating, reading, updating, and deleting 
 * survey responses only. While it may read information from other classes, the
 * only values the functions in this class should take and return should 
 * pertain to survey responses. Likewise, it should only change the state of
 * survey responses.
 * 
 * @author John Jenkins
 * @author Joshua Selsky
 * @author Hongsuda T. 
 */
public final class SurveyResponseServices {
	private static SurveyResponseServices instance;
	
	private IImageQueries imageQueries;
	private ISurveyUploadQuery surveyUploadQuery;
	private ISurveyResponseQueries surveyResponseQueries;
	private ISurveyResponseImageQueries surveyResponseImageQueries;
	
	/**
	 * Default constructor. Privately instantiated via dependency injection
	 * (reflection).
	 * 
	 * @throws IllegalStateException if an instance of this class already
	 * exists
	 * 
	 * @throws IllegalArgumentException if iImageQueries or iSurveyUploadQuery
	 * or iSurveyResponseQueries or iSurveyResponseImageQueries is null
	 */
	private SurveyResponseServices(IImageQueries iImageQueries, 
			                       ISurveyUploadQuery iSurveyUploadQuery,
			                       ISurveyResponseQueries iSurveyResponseQueries,
			                       ISurveyResponseImageQueries iSurveyResponseImageQueries) {
		if(instance != null) {
			throw new IllegalStateException("An instance of this class already exists.");
		}
		if(iImageQueries == null) {
			throw new IllegalArgumentException("An instance of IImageQueries is required.");
		}
		if(iSurveyUploadQuery == null) {
			throw new IllegalArgumentException("An instance of ISurveyUploadQuery is required.");
		}
		if(iSurveyResponseQueries == null) {
			throw new IllegalArgumentException("An instance of ISurveyResponseQueries is required.");
		}
		if(iSurveyResponseImageQueries == null) {
			throw new IllegalArgumentException("An instance of ISurveyResponseImageQueries is required.");
		}
		
		imageQueries = iImageQueries;
		surveyUploadQuery = iSurveyUploadQuery;
		surveyResponseQueries = iSurveyResponseQueries;
		surveyResponseImageQueries = iSurveyResponseImageQueries;
		
		instance = this;
	}
	
	/**
	 * @return  Returns the singleton instance of this class.
	 */
	public static SurveyResponseServices instance() {
		return instance;
	}
	
	/**
	 * Creates new survey responses in the database.
	 * 
	 * @param user The username of the user that created this survey response.
	 * 
	 * @param client The client value.
	 * 
	 * @param campaignUrn The unique identifier for the campaign to which the
	 * 					  survey belongs that the user used to create these
	 * 					  survey responses.
	 * 
	 * @param surveyUploadList The list of survey responses to add to the
	 * 						   database.
	 * 
	 * @param bufferedImageMap The map of image unique identifiers to 
	 * 						   BufferedImage objects to use when creating the
	 * 						   database entry.
	 * 
	 * @param videoContentsMap The map of the video unique identifiers to their
	 * 						   objects.
	 * 
	 * @param audioContentsMap The map of the audio unique identifiers to their
	 * 						   objects.
	 * @param fileContentsMap 
	 * 
	 * @return A list of the indices of the survey responses that were 
	 * 		   duplicates.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public List<Integer> createSurveyResponses(final String user, 
			final String client, final String campaignUrn,
            final List<SurveyResponse> surveyUploadList,
            final Map<UUID, Image> bufferedImageMap,
            final Map<UUID, Video> videoContentsMap,
            final Map<UUID, Audio> audioContentsMap, 
            final Map<UUID, IMedia> fileContentsMap) 
            throws ServiceException {
		
		try {
			return surveyUploadQuery.insertSurveys(
				user, 
				client, 
				campaignUrn, 
				surveyUploadList, 
				bufferedImageMap,
				videoContentsMap,
				audioContentsMap,
				fileContentsMap);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	
	/**
	 * Retrieves all of the survey response privacy states.
	 * 
	 * @return A list of the survey response privacy states.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public List<SurveyResponse.PrivacyState> getSurveyResponsePrivacyStates() 
			throws ServiceException {
		
		try {
			return surveyResponseQueries.retrieveSurveyResponsePrivacyStates();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that, for all photo prompt responses, a corresponding image
	 * exists in the list of images, and the file doesn't violate the
	 * prompt definition.
	 * 
	 * @param surveyResponses The survey responses.
	 * 
	 * @param images A map of image IDs to image contents.
	 * 
	 * @throws ServiceException Thrown if a prompt response exists but its
	 * 							corresponding contents don't.
	 */
	public void verifyImagesFilesForPhotoPromptResponses(
			final Collection<SurveyResponse> surveyResponses,
			final Map<UUID, Image> images) 
			throws ServiceException {
		
		verifyMediaFilesForMediaPromptResponses(PhotoPromptResponse.class, surveyResponses, images);
	/*	
		for(SurveyResponse surveyResponse : surveyResponses) {
			for(Response promptResponse : surveyResponse.getResponses().values()) {
				if(promptResponse instanceof PhotoPromptResponse) {
					Object responseValue = promptResponse.getResponse();
					if((responseValue instanceof UUID) && 
							(! images.containsKey(responseValue))) {
						throw new ServiceException(
								ErrorCode.SURVEY_INVALID_RESPONSES, 
								"An image was missing for a photo prompt response: " + responseValue.toString());
					}
				}
			}
		}
		*/
	}
	
	/**
	 * Verifies that, for all video prompt responses, a corresponding video
	 * exists in the list of videos, and the file doesn't violate the
	 * prompt definition.
	 * 
	 * @param surveyResponses The survey responses.
	 * 
	 * @param images A map of video IDs to video contents.
	 * 
	 * @throws ServiceException Thrown if a prompt response exists but its
	 * 							corresponding contents don't.
	 */
	public void verifyVideosFilesForVideoPromptResponses(
			final Collection<SurveyResponse> surveyResponses,
			final Map<UUID, Video> videos) 
			throws ServiceException {
		
		verifyMediaFilesForMediaPromptResponses(VideoPromptResponse.class, surveyResponses, videos);
	}
	
	/**
	 * Verifies that, for all audio prompt responses, a corresponding audio
	 * exists in the list of audio files, and the file doesn't violate the
	 * prompt definition.
	 * 
	 * @param surveyResponses The survey responses.
	 * 
	 * @param images A map of audio IDs to audio contents.
	 * 
	 * @throws ServiceException Thrown if a prompt response exists but its
	 * 							corresponding contents don't.
	 */
	public void verifyAudioFilesForAudioPromptResponses(
			final Collection<SurveyResponse> surveyResponses,
			final Map<UUID, Audio> audios) 
			throws ServiceException {
		verifyMediaFilesForMediaPromptResponses(AudioPromptResponse.class, surveyResponses, audios);
		
	}
	
	/**
	 * Verifies that, for all file prompt responses, a corresponding file
	 * exists in the list of OFile objects.
	 * 
	 * @param surveyResponses The survey responses.
	 * 
	 * @param images A map of audio IDs to audio contents.
	 * 
	 * @throws ServiceException Thrown if a prompt response exists but its
	 * 							corresponding contents don't.
	 */
	public void verifyOFilesForFilePromptResponses(
			final Collection<SurveyResponse> surveyResponses,
			final Map<UUID, IMedia> oFiles) 
			throws ServiceException {

		verifyMediaFilesForMediaPromptResponses(FilePromptResponse.class, surveyResponses, oFiles);
		
	}
	
	/**
	 * Verifies that, for each media prompt responses, a corresponding file/object
	 * exists in the list of media files, and the file doesn't violate the 
	 * prompt definition.
	 * 
	 * @param 	mediaClass The particular media class that we are checking against
	 * 
	 * @param	surveyResponses The survey responses.
	 * 
	 * @param	mediaMap A map of media ID to media content
	 * 
	 * @throws ServiceException Thrown if a prompt response exists but its
	 * 							corresponding contents don't.
	 */
	
	public void verifyMediaFilesForMediaPromptResponses(
			final Class<? extends MediaPromptResponse> mediaClass,
			final Collection<SurveyResponse> surveyResponses,
			final Map<UUID, ? extends IMedia> mediaMap) 
			throws ServiceException {
		
			
		for(SurveyResponse surveyResponse : surveyResponses) {
			for(Response promptResponse : surveyResponse.getResponses().values()) {
				if (mediaClass.isInstance(promptResponse))	{
					Object responseValue = promptResponse.getResponse();
					if(responseValue instanceof UUID) {
						if (mediaMap.containsKey(responseValue)) {
							// validate the media against the xml
							try {
								PromptResponse pr = (PromptResponse)promptResponse;
								MediaPrompt dp = (MediaPrompt) (pr.getPrompt());
								dp.validateMediaFileSize(mediaMap.get(responseValue));
							} catch (DomainException e){
								throw new ServiceException(e);
							} catch (Exception e) {
								throw new ServiceException("Can't convert prompt to MediaPrompt", e);
							}	
							
						} else { // no media content
							throw new ServiceException(
									ErrorCode.SURVEY_INVALID_RESPONSES, 
									"A file was missing for a " + mediaClass.getSimpleName() + " prompt response: " + 
											responseValue.toString());
						}
					}
						
				} 
			}
		}
			
	}

	
	/**
	 * Generates a list of SurveyResponse objects where each object
	 * represents an individual survey response and the list is the result of
	 * the optional parameters below. The campaign ID is required because the
	 * largest scale on which survey responses can be queried is by campaign.
	 * <br />
	 * <br />
	 * The remaining parameters are all optional as they further reduce the 
	 * list. For instance, if all optional parameters were null, the list would
	 * be all of the survey responses in the campaign. If the username were set
	 * to a specific user, the result would be all of the survey responses made
	 * by that user. If the username were set to a specific user and the start
	 * date were set to some date, the result would be all of the survey 
	 * responses made by that user on and after that date.<br />
	 * <br />
	 * If two parameters compete, like the start date is after the end date or
	 * the survey ID and prompt ID are present but that prompt is not part of
	 * that survey, no error will be thrown but the results will be empty.
	 *  
	 * @param campaignId The campaign's unique identifier. Required.
	 * 
	 * @param username The username of the user that is making this request.
	 * 				   This is used by the ACLs to limit who sees what. 
	 * 				   Required.
	 * 
	 * @param surveyResponseIds A set of survey response unique identifiers 
	 * 							limiting the results to only those survey
	 * 							responses whose IDs are in this list. Optional.
	 * 
	 * @param usernames A user's username to which the results must only 
	 * 				   pertain. Optional.
	 * 
	 * @param startDate A date which limits the responses to those generated 
	 * 					on or after. Optional.
	 * 
	 * @param endDate An date which limits the responses to those generated on
	 * 				  or before. Optional.
	 * 
	 * @param privacyState A survey response privacy state that limits the 
	 * 					   results to only those with this privacy state.
	 * 					   Optional.
	 * 
	 * @param surveyIds A collection of survey response IDs to which the 
	 * 					results must belong to any of them. Optional.
	 * 
	 * @param promptIds A collection of prompt response IDs to which the 
	 * 					results must belong to any of them.
	 * 
	 * @param promptType A prompt type that limits all responses to those of
	 * 					 exactly this prompt type. Optional.
	 * 
	 * @param surveyResponseSearchTokens The set of tokens to use against the
	 * 									 prompt response values. Optional.
	 * 
	 * @param columns Aggregates the data based on the column keys. If this is
	 * 				  null, no aggregation is performed. If the list is empty,
	 * 				  an empty list is returned.
	 * 
	 * @param sortOrder The order in which to sort the responses.
	 * 
	 * @param surveyResponsesToSkip The number of survey responses to skip once
	 * 								the result has been aggregated from the 
	 * 								server.
	 * 
	 * @param surveyResponsesToProcess The number of survey responses to 
	 * 								   analyze once the survey responses to 
	 * 								   skip have been skipped.
	 * 
	 * @param result A list of SurveyResponse objects, probably empty, to add
	 * 				 the results of this query to.
	 * 
	 * @return The total number of results that matched the given criteria, not
	 * 		   the number that were added to result. In order to get that 
	 * 		   number, simply subtract 'result's length after this to call to
	 * 		   its length before this call.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public int readSurveyResponseInformation(
			final Campaign campaign,
			final String username,
			final Set<UUID> surveyResponseIds,
			final Collection<String> usernames,
			final DateTime startDate, final DateTime endDate, 
			final SurveyResponse.PrivacyState privacyState, 
			final Collection<String> surveyIds, 
			final Collection<String> promptIds, 
			final String promptType,
			final Set<String> promptResponseSearchTokens,
			final Collection<ColumnKey> columns, 
			final List<SortParameter> sortOrder,
			final long surveyResponsesToSkip,
			final long surveyResponsesToProcess,
			final List<SurveyResponse> result) 
			throws ServiceException {
		
		try {
			return surveyResponseQueries.retrieveSurveyResponses(
					campaign, 
					username,
					surveyResponseIds,
					usernames, 
					startDate, 
					endDate, 
					privacyState, 
					surveyIds, 
					promptIds, 
					promptType,
					promptResponseSearchTokens,
					columns,
					sortOrder,
					surveyResponsesToSkip,
					surveyResponsesToProcess,
					result);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Updates the privacy state on a survey.
	 * 
	 * @param surveyResponseIds  The key for the survey responses to update.
	 * @param privacyState  The new privacy state value.
	 * @throws ServiceException  If an error occurs.
	 */
	public void updateSurveyResponsesPrivacyState(
			final Set<UUID> surveyResponseIds, 
			final SurveyResponse.PrivacyState privacyState) 
			throws ServiceException {
		
		try {
			surveyResponseQueries.updateSurveyResponsesPrivacyState(surveyResponseIds, privacyState);
		} 
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Deletes all of the images associated with a survey response then deletes
	 * the survey response itself.
	 * 
	 * @param surveyResponseId The survey response's unique identifier.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void deleteSurveyResponse(final UUID surveyResponseId) 
			throws ServiceException {
		
		try {
			List<UUID> imageIds = surveyResponseImageQueries.getImageIdsFromSurveyResponse(surveyResponseId);

			// TODO:
			// Here we are deleting the images then deleting the survey 
			// response. If this fails, the entire service is aborted, but the
			// individual services that deleted the images are not rolled back.
			// This leads to an appropriate division of labor in the code; 
			// otherwise, the deleteSurveyResponse() function would need to 
			// modify the url_based_resource table and file system. If an image
			// deletion fails, the survey response will exist, but the images 
			// from the survey response that were deleted will be deleted. Not 
			// all of them will be deleted, however; the one that caused the
			// exception will remain. While this may seem like an error, I felt
			// that it would be worse to have the user stuck in a situation 
			// where no images were being deleted than one where only part of a
			// survey response was deleted. If the user is trying to delete an
			// image, this will give them the chance to delete it even if 
			// another image and/or the survey response are causing a problem.
			for(UUID imageId : imageIds) {
				if(imageId != null) {
					imageQueries.deleteImage(imageId);
				}
			}
			
			surveyResponseQueries.deleteSurveyResponse(surveyResponseId);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Gets the campaign ID for a survey response ID.
	 * 
	 * @param surveyResponseId The survey response's unique identifier.
	 * 
	 * @return The campaign's unique identifier.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public String getCampaignForSurveyResponseId(UUID surveyResponseId) 
			throws ServiceException {
		try {
			return surveyResponseQueries.getCampaignIdForSurveyResponseId(surveyResponseId);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Update existing survey responses in the database.
	 * 
	 * @param user The username of the user that created this survey response.
	 * 
	 * @param client The client value.
	 * 
	 * @param campaignUrn The unique identifier for the campaign to which the
	 * 					  survey belongs that the user used to create these
	 * 					  survey responses.
	 * 
	 * @param surveyUploadList The list of survey responses to add to the
	 * 						   database.
	 * 
	 * @param bufferedImageMap The map of image unique identifiers to 
	 * 						   BufferedImage objects to use when creating the
	 * 						   database entry.
	 * 
	 * @param videoContentsMap The map of the video unique identifiers to their
	 * 						   objects.
	 * 
	 * @param audioContentsMap The map of the audio unique identifiers to their
	 * 						   objects.
	 * @param fileContentsMap 
	 * 
	 * @return A list of the indices of the survey responses that were 
	 * 		   duplicates.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void updateSurveyResponses(
		final String username, 
		final String client, 
		final Campaign campaign,
		final List<SurveyResponse> surveyUploadList,
		final Map<UUID, Image> bufferedImageMap,
		final Map<UUID, Video> videoContentsMap,
		final Map<UUID, Audio> audioContentsMap, 
		final Map<UUID, IMedia> fileContentsMap) 
		throws ServiceException {

	    	// check that the upload list is not empty
	    
		try {
		    
		    // retrieve and check existing survey responses
		    List<SurveyResponse> result = new LinkedList<SurveyResponse>();
		    Set<UUID> surveyResponseIds = new HashSet<UUID>();
		    for(SurveyResponse surveyResponse : surveyUploadList) {
			surveyResponseIds.add(surveyResponse.getSurveyResponseId());
		    }
		    
		    if (surveyResponseIds.size() != surveyUploadList.size()) {
			// Throw an error.. There are duplicate entries in the upload list
			throw new ServiceException(
				ErrorCode.SURVEY_INVALID_RESPONSES, 
				"Some responses in the list are duplicated");
		    }
		    
		    // Retrieve existing surveys based on the response IDs. If one is missing, throw an error.
		    // Access control: Only search for responses that belong to the owner only. 
		    readSurveyResponseInformation(
			campaign,
			username,
			surveyResponseIds, // Set<UUID>
			Arrays.asList(username), // name username only,
			null, null,	// startDate, endDate, 
			null, 		// privacyState, 
			null, 		// surveyIds, 
			null, 		// promptIds, 
			null, 		// promptType,
			null, 		// promptResponseSearchTokens,
			null,		// columns, 
			null, 		// sortOrder,
			0,		// final long surveyResponsesToSkip,
			surveyUploadList.size(), // final long surveyResponsesToProcess,
			result); 

		    if (result.size() != surveyUploadList.size()){
			// throw an error. Some entries do not exist. 
			throw new ServiceException(
				ErrorCode.SURVEY_INVALID_RESPONSES, 
				"Some responses in the list are not found. Only the owner can update his/her data.");
		    }
		    
		    // create a map of existing responses for easy access
		    Map<UUID, SurveyResponse> existingResponseMap = new HashMap<UUID, SurveyResponse>();
		    for(SurveyResponse surveyResponse : result) {
			existingResponseMap.put(surveyResponse.getSurveyResponseId(), surveyResponse);
		    }
		    
		    // don't support repeatable set
		    
		    for(SurveyResponse updatedResponse : surveyUploadList) {
			SurveyResponse surveyResponse = existingResponseMap.get(updatedResponse.getSurveyResponseId());

			// make sure that they are from the same survey
			if (surveyResponse.getSurvey().getId() != updatedResponse.getSurvey().getId()){
			    // Mismatched surveys: throw an error message.
			    throw new ServiceException(
				    ErrorCode.SURVEY_INVALID_RESPONSES, 
				    "Some responses do not exist. The existing response to be updated belong to different surveys");
			}
			
			// Users can only update their own responses.
			// No need for this check since username is specified in the search criteria
			if (updatedResponse.getUsername() != username) {
			    throw new ServiceException(
				    ErrorCode.SURVEY_INVALID_RESPONSES, 
				    "Only the response owners can update their responses");
			}
			
			
		    }
		    
		    // update all the db entries and files (if applicable) in the system
		    surveyUploadQuery.updateSurveys(
				username, 
				client, 
				campaign.getId(),
				surveyUploadList, 
				bufferedImageMap,
				videoContentsMap,
				audioContentsMap,
				fileContentsMap, 
				existingResponseMap);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	

}
