package org.ohmage.domain;

import java.util.UUID;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>
 * The authorization token for a user to grant access to some scope to some
 * third-party.
 * </p>
 * 
 * <p>
 * This class is immutable.
 * </p>
 *
 * @author John Jenkins
 */
public class AuthorizationToken extends OhmageDomainObject {
	/**
	 * The default number of milliseconds that a token should live.
	 */
	public static final long DEFAULT_TOKEN_LIFETIME_MILLIS = 1000 * 60 * 60;

	/**
	 * The JSON key for the authorization code that backs this authorization
	 * token.
	 */
	public static final String JSON_KEY_AUTHORIZATION_CODE = 
		"authorization_code";

	/**
	 * The JSON key for the access token.
	 */
	public static final String JSON_KEY_ACCESS_TOKEN = "access_token";
	/**
	 * The JSON key for the refresh token.
	 */
	public static final String JSON_KEY_REFRESH_TOKEN = "refresh_token";
	/**
	 * The JSON key for the time the token was created.
	 */
	public static final String JSON_KEY_CREATION_TIME = "creation_time";
	/**
	 * The JSON key for the time the token expires.
	 */
	public static final String JSON_KEY_EXPIRATION_TIME = "expiration_time";
	
	/**
	 * The authorization code that backs this authorization token. When
	 * referencing or refreshing this token, this code and its corresponding
	 * response should be consulted to determine the scope and whether or not
	 * this authorization has been revoked.
	 */
	@JsonProperty(JSON_KEY_AUTHORIZATION_CODE)
	private final String authorizationCode;
	/**
	 * The access token.
	 */
	@JsonProperty(JSON_KEY_ACCESS_TOKEN)
	private final String accessToken;
	/**
	 * The refresh token.
	 */
	@JsonProperty(JSON_KEY_REFRESH_TOKEN)
	private final String refreshToken;
	/**
	 * The number of milliseconds since the epoch at which time this token was
	 * created.
	 */
	@JsonProperty(JSON_KEY_CREATION_TIME)
	private final long creationTime;
	/**
	 * The number of milliseconds since the epoch at which time this token
	 * expires.
	 */
	@JsonProperty(JSON_KEY_EXPIRATION_TIME)
	private final long expirationTime;
	
	/**
	 * Creates a new authentication token with new access and refresh tokens
	 * and new creation and expiration times. This is not a copy constructor.
	 * This is designed for creating new tokens via a refresh.
	 * 
	 * @param oldToken
	 *        The old token that will be used to create the new token.
	 * 
	 * @throws IllegalArgumentException
	 *         The old token was null or already invalidated.
	 */
	public AuthorizationToken(
		final AuthorizationToken oldToken)
		throws IllegalArgumentException {
		
		// Pass through to the builder constructor.
		this(
			oldToken.authorizationCode,
			UUID.randomUUID().toString(),
			UUID.randomUUID().toString(),
			System.currentTimeMillis(),
			System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MILLIS,
			null);
	}
	
	/**
	 * Creates an authorization token presumably from an existing one since all
	 * of the fields are given. To create a new token, it is recommended that
	 * {@link #AuthorizationToken(AuthorizationCodeResponse)} or
	 * {@link #AuthorizationToken(AuthorizationToken)} be used.
	 * 
	 * @param authorizationCode
	 *        The unique identifier for the authorization code that backs this
	 *        token.
	 * 
	 * @param accessToken
	 *        The access token value for this authorization token.
	 * 
	 * @param refreshToken
	 *        The refresh token value for this authorization token.
	 * 
	 * @param creationTime
	 *        The number of milliseconds since the epoch at which time this
	 *        token was created.
	 * 
	 * @param expirationTime
	 *        The number of milliseconds since the epoch at which time this
	 *        token expires.
	 * 
	 * @throws IllegalArgumentException
	 *         A parameter is invalid.
	 * 
	 * @see #AuthorizationToken(AuthorizationCodeResponse)
	 * @see #AuthorizationToken(AuthorizationToken)
	 */
	@JsonCreator
	public AuthorizationToken(
		@JsonProperty(JSON_KEY_AUTHORIZATION_CODE)
			final String authorizationCode,
		@JsonProperty(JSON_KEY_ACCESS_TOKEN) final String accessToken,
		@JsonProperty(JSON_KEY_REFRESH_TOKEN) final String refreshToken,
		@JsonProperty(JSON_KEY_CREATION_TIME) final long creationTime,
		@JsonProperty(JSON_KEY_EXPIRATION_TIME) final long expirationTime,
		@JsonProperty(JSON_KEY_INTERNAL_VERSION) final Long internalVersion)
		throws IllegalArgumentException {

		// Pass through to the builder constructor.
		this(
			authorizationCode,
			accessToken,
			refreshToken,
			creationTime,
			expirationTime,
			internalVersion,
			null);
	}
	
	/**
	 * Builds the AuthorizationToken object.
	 * 
	 * @param authorizationCode
	 *        The unique identifier for the authorization code that backs this
	 *        token.
	 * 
	 * @param accessToken
	 *        The access token value for this authorization token.
	 * 
	 * @param refreshToken
	 *        The refresh token value for this authorization token.
	 * 
	 * @param creationTime
	 *        The number of milliseconds since the epoch at which time this
	 *        token was created.
	 * 
	 * @param expirationTime
	 *        The number of milliseconds since the epoch at which time this
	 *        token expires.
	 * 
	 * @param internalReadVersion
	 *        The internal version of this authentication token when it was
	 *        read from the database.
	 * 
	 * @param internalWriteVersion
	 *        The new internal version of this authentication token when it
	 *        will be written back to the database.
	 * 
	 * @throws IllegalArgumentException
	 *         The token and/or user-name are null, the token is being granted
	 *         in the future, or the token is being granted after it has
	 *         expired.
	 */
	private AuthorizationToken(
		final String authorizationCode,
		final String accessToken,
		final String refreshToken,
		final long creationTime,
		final long expirationTime,
		final Long internalReadVersion,
		final Long internalWriteVersion)
		throws IllegalArgumentException {
		
		// Pass the versioning parameters to the parent.
		super(internalReadVersion, internalWriteVersion);
		
		// Validate the parameters.
		if(authorizationCode == null) {
			throw
				new IllegalArgumentException("The authorization code is null.");
		}
		if(accessToken == null) {
			throw new IllegalArgumentException("The access token is null.");
		}
		if(refreshToken == null) {
			throw new IllegalArgumentException("The refresh token is null.");
		}
		
		DateTime creationTimeDateTime = new DateTime(creationTime);
		if(creationTimeDateTime.isAfterNow()) {
			throw
				new IllegalArgumentException(
					"The token's creation time cannot be in the future.");
		}
		if(creationTimeDateTime.isAfter(expirationTime)) {
			throw
				new IllegalArgumentException(
					"The token's expiration time cannot be before its " +
						"creation time.");
		}
		
		this.authorizationCode = authorizationCode;
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.creationTime = creationTime;
		this.expirationTime = expirationTime;
	}
	
	/**
	 * Returns the authorization code that backs this authorization token.
	 * 
	 * @return The authorization code that backs this authorization token as a
	 *         string.
	 */
	public String getAuthorizationCodeString() {
		return authorizationCode;
	}
	
	/**
	 * Returns the access token.
	 * 
	 * @return The access token.
	 */
	public String getAccessToken() {
		return accessToken;
	}
	
	/**
	 * Returns the refresh token.
	 * 
	 * @return The refresh token.
	 */
	public String getRefreshToken() {
		return refreshToken;
	}
	
	/**
	 * Returns the time that the token was created.
	 * 
	 * @return The time that the token was created.
	 */
	public long getCreationTime() {
		return creationTime;
	}
	
	/**
	 * Returns the time that the token was/will expire.
	 * 
	 * @return The time that the token was/will expire.
	 */
	public long getExpirationTime() {
		return expirationTime;
	}
	
	/**
	 * Returns the number of milliseconds before the access token expires.
	 * 
	 * @return The number of milliseconds before the access token expires. This
	 *         may be negative if the token has already expired.
	 */
	public long getExpirationIn() {
		return expirationTime - System.currentTimeMillis();
	}
	
	/**
	 * Returns whether or not the token is still valid.
	 * 
	 * @return Whether or not the token is still valid.
	 */
	public boolean isValid() {
		return expirationTime < System.currentTimeMillis();
	}
}