package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.cache.Cache;
import utils.DB;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user)) {
			System.out.println("BAD REQUEST CREATE USER");
			return error(BAD_REQUEST);
		}

		Log.info(() -> format("sporting: \n"));

		Result<User> dbResult = DB.insertOne(user);
		if (dbResult.isOK()) {
			Cache.insertOne(user);
		}

		/*
		 * try (Jedis jedis = RedisCache.getCachePool().getResource()) {
		 * var key = "users:" + user.getUserId();
		 * var value = JSON.encode(user);
		 * jedis.set(key, value);
		 * 
		 * jedis.lpush(MOST_RECENT_USERS_LIST, value);
		 * if (jedis.llen(MOST_RECENT_USERS_LIST) > 5) {
		 * jedis.ltrim(MOST_RECENT_USERS_LIST, 0, 4);
		 * }
		 * }
		 * }
		 */

		return errorOrValue(dbResult, user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		Result<User> result = Cache.getOne(userId, User.class);

		if (!result.isOK()) {
			result = DB.getOne(userId, User.class);
		}

		/*
		 * try (Jedis jedis = RedisCache.getCachePool().getResource()) {
		 * var key = "users:" + userId;
		 * var value = jedis.get(key);
		 * if (value != null) {
		 * User user = JSON.decode(value, User.class);
		 * return validatedUserOrError(Result.ok(user), pwd);
		 * }
		 * }
		 */

		return validatedUserOrError(result, pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd),
				user -> {
					var updatedUser = user.updateFrom(other);

					Result<User> dbResult = DB.updateOne(updatedUser);
					if (dbResult.isOK()) {
						Cache.updateOne(updatedUser);
					}

					/*
					 * try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					 * var key = "users:" + userId;
					 * var value = JSON.encode(updatedUser);
					 * jedis.set(key, value);
					 * }
					 */

					return dbResult;
				});
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			Result<User> dbResult = DB.deleteOne(user);

			if (dbResult.isOK()) {
				Cache.deleteOne(user);
			}

			/*
			 * try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			 * var key = "users:" + userId;
			 * jedis.del(key);
			 * }
			 */

			return dbResult;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.value()
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res.isOK())
			return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUserInfo(User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
	}
}
