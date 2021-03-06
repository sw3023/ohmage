package org.ohmage.domain.campaign.prompt;

import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.config.grammar.custom.ConditionValuePair;
import org.ohmage.domain.campaign.PromptResponse;
import org.ohmage.domain.campaign.Response.NoResponse;
import org.ohmage.domain.campaign.response.VideoPromptResponse;
import org.ohmage.exception.DomainException;

/**
 * This class represents a video prompt.
 *
 * @author John Jenkins
 * @author Hongsuda T.
 */
public class VideoPrompt extends MediaPrompt {
	private static final String JSON_KEY_MAX_SECONDS = "max_seconds";
	
	/**
	 * The key for the properties to retrieve the maximum seconds value.
	 */
	public static final String XML_MAX_SECONDS = "maxSeconds";
	
	/**
	 * The maximum number of seconds that the recording may last.
	 */
	private final Integer maxSeconds;


	/**
	 * Creates a new video prompt.
	 * 
	 * @param id The unique identifier for the prompt within its survey item
	 * 			 group.
	 * 
	 * @param condition The condition determining if this prompt should be
	 * 					displayed.
	 * 
	 * @param unit The unit value for this prompt.
	 * 
	 * @param text The text to be displayed to the user for this prompt.
	 * 
	 * @param explanationText A more-verbose version of the text to be 
	 * 						  displayed to the user for this prompt.
	 * 
	 * @param skippable Whether or not this prompt may be skipped.
	 * 
	 * @param skipLabel The text to show to the user indicating that the prompt
	 * 					may be skipped.
	 * 
	 * @param displayLabel The display label for this prompt.
	 * 
	 * @param maxSeconds The maximum number of seconds allowed for this video.
	 * 
	 * @param index This prompt's index in its container's list of survey 
	 * 				items.
	 * 
	 * @throws DomainException Thrown if the maximum number of seconds is 
	 * 						   negative.
	 */
	public VideoPrompt(
			final String id,
			final String condition,
			final String unit,
			final String text,
			final String explanationText,
			final boolean skippable,
			final String skipLabel,
			final String displayLabel,
			final int index,
			final Integer maxSeconds,
			final Long maxFileSize	) 
			throws DomainException {
		
		super(
			id,
			condition,
			unit,
			text,
			explanationText,
			skippable,
			skipLabel,
			displayLabel,
			Type.VIDEO,
			index, 
			maxFileSize);
		
		if((maxSeconds != null) && (maxSeconds <= 0)) {
			throw new DomainException(
				"The maximum number of seconds must be a positive integer.");
		}
		this.maxSeconds = maxSeconds;
		
	}
	
	/**
	 * Returns the maximum number of seconds of video allowed.
	 * 
	 * @return The maximum number of seconds of video allowed.
	 */
	public Integer getMaxSeconds() {
		return maxSeconds;
	}

	/**
	 * Conditions are not allowed for this prompt type unless they are
	 * {@link NoResponse} values.
	 */
	@Override
	public void validateConditionValuePair(
			final ConditionValuePair pair)
			throws DomainException {
		
		throw new DomainException(
			"Conditions are not allowed for video prompts.");
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.domain.campaign.Prompt#createResponse(java.lang.Integer, java.lang.Object)
	 */
	@Override
	public PromptResponse createResponse(
			final Integer repeatableSetIteration,
			final Object response)
			throws DomainException {
		
		return new VideoPromptResponse(
			this,
			repeatableSetIteration,
			response);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.domain.campaign.Prompt#toJson()
	 */
	
	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject result = super.toJson();
		
		result.put(JSON_KEY_MAX_SECONDS, maxSeconds);
		
		return result;
	}

	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result +
				((maxSeconds == null) ? 0 : maxSeconds.hashCode());	

		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if(this == obj) {
			return true;
		}
		if(!super.equals(obj)) {
			return false;
		}
		if(!(obj instanceof VideoPrompt)) {
			return false;
		}
		VideoPrompt other = (VideoPrompt) obj;
		if (maxSeconds == null) {
			if (other.maxSeconds != null)
				return false;
		} else {
			if(! maxSeconds.equals(other.maxSeconds)) {
				return false;
			}
		}
		return true;
	}
}
