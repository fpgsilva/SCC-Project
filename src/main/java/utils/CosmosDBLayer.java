
package utils;

import java.util.List;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;

import java.util.function.Supplier;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

public class CosmosDBLayer {

	private static final String CONNECTION_URL = "https://cosmos70525.documents.azure.com:443/";
	private static final String DB_KEY = "8BJx0GsRdzts95nzS81jcE5UZrzqZgz7Rscc0bPMX6D4J7OWcNPT7XgPDEvoLaGahKdlGM0HeYiBACDb2skHJg==";
	private static final String DB_NAME = "cosmosdb70525";

	private static final String USERS_CONTAINER = "users";
	private static final String SHORTS_CONTAINER = "shorts";
	private static final String FOLLOWS_CONTAINER = "follows";
	private static final String LIKES_CONTAINER = "likes";

	private static CosmosDBLayer instance;

	public static synchronized CosmosDBLayer getInstance() {
		if (instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
				.endpoint(CONNECTION_URL)
				.key(DB_KEY)
				// .directMode()
				.gatewayMode()
				// replace by .directMode() for better performance
				.consistencyLevel(ConsistencyLevel.SESSION)
				.connectionSharingAcrossClientsEnabled(true)
				.contentResponseOnWriteEnabled(true)
				.buildClient();
		instance = new CosmosDBLayer(client);
		return instance;

	}

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer container;
	private CosmosContainer users_container;
	private CosmosContainer shorts_container;
	private CosmosContainer follows_container;
	private CosmosContainer likes_container;

	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}

	private synchronized void init() {
		if (db != null)
			return;
		db = client.getDatabase(DB_NAME);
		users_container = db.getContainer(USERS_CONTAINER);
		shorts_container = db.getContainer(SHORTS_CONTAINER);
		follows_container = db.getContainer(FOLLOWS_CONTAINER);
		likes_container = db.getContainer(LIKES_CONTAINER);

	}

	public void close() {
		client.close();
	}

	private void selectContainer(String containerId) {

		switch (containerId) {
			case "tukano.api.User":
				System.out.println("called user container\n");
				container = users_container;
				break;

			case "tukano.api.Short":
				container = shorts_container;
				break;

			case "tukano.api.Follows":
				container = follows_container;
				break;

			case "tukano.api.Likes":
				container = likes_container;
				break;

			default:
				System.out.println("called default\n");
				break;
		}
	}

	public <T> Result<T> getOne(String id, Class<T> clazz) {
		var cId = clazz.getName();
		// var selectedContainer = selectContainer(cId);
		return tryCatch(() -> container.readItem(id, new PartitionKey(id), clazz).getItem(), cId);
	}

	public <T> Result<?> deleteOne(T obj) {
		var cl = obj.getClass().getName();
		return tryCatch(() -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem(), cl);
	}

	public <T> Result<T> updateOne(T obj) {
		var cl = obj.getClass().getName();

		return tryCatch(() -> container.upsertItem(obj).getItem(), cl);
	}

	public <T> Result<T> insertOne(T obj) {
		System.out.println("INSERT ONE COSMOS LAYER");
		var cId = obj.getClass().getName();
		return tryCatch(() -> container.createItem(obj).getItem(), cId);
	}

	public <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
		var cl = clazz.getName();
		return tryCatch(() -> {
			var res = container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		}, cl);
	}

	<T> Result<T> tryCatch(Supplier<T> supplierFunc, String classString) {
		try {
			init();
			selectContainer(classString);
			return Result.ok(supplierFunc.get());
		} catch (CosmosException ce) {
			// ce.printStackTrace();
			System.out.println(ce);
			System.out.println("YOYO COSMOS EXCEPTION AQUI");
			return Result.error(errorCodeFromStatus(ce.getStatusCode()));
		} catch (Exception x) {
			System.out.println("EXCEPTION X TRY CATCH");
			x.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}

	static Result.ErrorCode errorCodeFromStatus(int status) {
		return switch (status) {
			case 200 -> ErrorCode.OK;
			case 404 -> ErrorCode.NOT_FOUND;
			case 409 -> ErrorCode.CONFLICT;
			default -> ErrorCode.INTERNAL_ERROR;
		};
	}
}
