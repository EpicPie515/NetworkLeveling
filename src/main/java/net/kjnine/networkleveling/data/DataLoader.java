package net.kjnine.networkleveling.data;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonValue;
import org.bson.Document;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public abstract class DataLoader implements Closeable {
	
	private ConnectionString connString = null;
	private String dbName = "";
	private String tableName = "";
	
	private DataType type;
	
	public DataLoader(DataType dataType) {
		type = dataType;
	}
	
	public DataType getType() {
		return type;
	}
	
	@Override
	public abstract void close();
	
	/**
	 * Not required for flatfile.
	 */
	private void setConnectionSettings(String address, String port, 
			String name, String user, String pass, String table) {
		connString = new ConnectionString("mongodb://" + address + ":" + port + "/");
		dbName = name;
		tableName = table;
	}
	
	public static enum DataType {
		MONGODB(MongoDB.class), 
		MYSQL(MySQL.class), 
		FLATFILE(FlatFile.class);
		
		private Class<?> clazz;
		DataType(Class<?> clazz) {
			this.clazz = clazz;
		}
		
		public Class<?> getLoaderClass() {
			return clazz;
		}
	}
	
	/**
	 * Gets all data where the key's value == value
	 */
	public abstract Set<JsonElement> getData(String key, BsonValue value);
	
	/**
	 * @return finds first where keyvalue matches value, then sets the elements
	 */
	public abstract boolean setData(String keyWhere, BsonValue valueWhere, List<BsonElement> elements);
	
	public static class MongoDB extends DataLoader {

		private MongoClient mongoClient;
		
		public MongoDB(String address, int port, 
				String name, String user, String pass, String table) {
			super(DataType.MONGODB);
			super.setConnectionSettings(address, String.valueOf(port), name, user, pass, table);
			MongoCredential credential = MongoCredential.createCredential(user, name, pass.toCharArray());

		    MongoClientSettings settings = MongoClientSettings.builder()
		    		.applyConnectionString(super.connString)
		            .credential(credential)
		            .applyToSslSettings(builder -> builder.enabled(true))
		            .build();

		    mongoClient = MongoClients.create(settings);
		}

		@Override
		public Set<JsonElement> getData(String key, BsonValue value) {
			MongoDatabase mongodb = mongoClient.getDatabase(super.dbName);
			MongoCollection<Document> mongoTable = mongodb.getCollection(super.tableName);
			FindIterable<Document> res = mongoTable.find(new BsonDocument(key, value));
			Set<JsonElement> out = new HashSet<>();
			JsonParser parser = new JsonParser();
			res.forEach(doc -> out.add(parser.parse(doc.toJson())));
			return out;
		}

		@Override
		public void close() {
			mongoClient.close();
		}

		@Override
		public boolean setData(String keyWhere, BsonValue valueWhere, List<BsonElement> elements) {
			MongoDatabase mongodb = mongoClient.getDatabase(super.dbName);
			MongoCollection<Document> mongoTable = mongodb.getCollection(super.tableName);
			Document d = mongoTable.findOneAndUpdate(new BsonDocument(keyWhere, valueWhere), new BsonDocument(elements));
			return d != null;
		}
	}
	
	public static class MySQL extends DataLoader {

		private HikariDataSource dataSource;
	 
	    private int minimumConnections;
	    private int maximumConnections;
	    private long connectionTimeout;
	 
	    public MySQL(String address, int port, String name, String user, String pass, 
	    		String table, int minConns, int maxConns, long timeout) {
	    	super(DataType.MYSQL);
	    	super.dbName = name;
	    	super.tableName = table;
			this.minimumConnections = minConns;
			this.maximumConnections = maxConns;
			this.connectionTimeout = timeout;
	        setupPool(address, String.valueOf(port), name, user, pass);
		}
	 
	    private void setupPool(String address, String port, String name, String user, String pass) {
	        HikariConfig config = new HikariConfig();
	        config.setJdbcUrl(
	                "jdbc:mysql://" +
	                        address +
	                        ":" +
	                        port +
	                        "/" +
	                        name +
	                        "?useSSL=true"
	        );
	        config.setDriverClassName("com.mysql.jdbc.Driver");
	        config.setUsername(user);
	        config.setPassword(pass);
	        config.setMinimumIdle(minimumConnections);
	        config.setMaximumPoolSize(maximumConnections);
	        config.setConnectionTimeout(connectionTimeout);
	        dataSource = new HikariDataSource(config);
	    }
	 
	    private Connection getConnection() throws SQLException {
	        return dataSource.getConnection();
	    }
	 
	    private void closeConnection(Connection conn) {
	        if (conn != null) 
	        	try { 
	        		conn.close(); 
	        	} catch (SQLException ex) {ex.printStackTrace();}
	    }

		@Override
		public void close() {
			dataSource.close();
		}

		@Override
		public Set<JsonElement> getData(String key, BsonValue value) {
			Set<JsonElement> out = new HashSet<>();
			try {
				Connection c = getConnection();
				PreparedStatement ps = c.prepareStatement("SELECT * FROM ? WHERE ?=?");
				ps.setString(1, super.tableName);
				ps.setString(2, key.toUpperCase());
				if(value.isString()) ps.setString(3, value.asString().getValue());
				else if(value.isBoolean()) ps.setBoolean(3, value.asBoolean().getValue());
				else if(value.isDocument()) ps.setString(3, value.asDocument().toJson());
				else if(value.isDouble()) ps.setDouble(3, value.asDouble().getValue());
				else if(value.isInt32()) ps.setInt(3, value.asInt32().getValue());
				else if(value.isInt64()) ps.setLong(3, value.asInt64().getValue());
				else if(value.isNull()) ps.setNull(3, java.sql.Types.VARCHAR);
				else if(value.isTimestamp()) ps.setTimestamp(3, new Timestamp(value.asTimestamp().getValue()));
				else ps.setString(3, value.toString());
				ResultSet rs = ps.executeQuery();
				ResultSetMetaData md = rs.getMetaData();
				JsonParser p = new JsonParser();
				while(rs.next()) {
					JsonObject j = new JsonObject();
					for(int i = 0; i < md.getColumnCount(); i++) {
						j.add(md.getColumnLabel(i+1), p.parse(rs.getObject(i+1).toString()));
					}
					out.add(j);
				}
				rs.close();
				ps.close();
				closeConnection(c);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return out;
		}

		@Override
		public boolean setData(String keyWhere, BsonValue valueWhere, List<BsonElement> elements) {
			try {
				Connection c = getConnection();
				String[] qs = new String[elements.size()];
				for(int i = 0; i < qs.length; i++) qs[i] = "?=?";
				String qsc = String.join(",", qs);
				PreparedStatement ps = c.prepareStatement("UPDATE ? SET " + qsc + " WHERE ?=?");
				int i = 1;
				ps.setString(i++, super.tableName);
				for(BsonElement el : elements) {
					ps.setString(i++, el.getName());
					BsonValue value = el.getValue();
					if(value.isString()) ps.setString(i, value.asString().getValue());
					else if(value.isBoolean()) ps.setBoolean(i, value.asBoolean().getValue());
					else if(value.isDocument()) ps.setString(i, value.asDocument().toJson());
					else if(value.isDouble()) ps.setDouble(i, value.asDouble().getValue());
					else if(value.isInt32()) ps.setInt(i, value.asInt32().getValue());
					else if(value.isInt64()) ps.setLong(i, value.asInt64().getValue());
					else if(value.isNull()) ps.setNull(i, java.sql.Types.VARCHAR);
					else if(value.isTimestamp()) ps.setTimestamp(i, new Timestamp(value.asTimestamp().getValue()));
					else ps.setString(i, value.toString());
					i++;
				}
				ps.setString(i++, keyWhere);
				if(valueWhere.isString()) ps.setString(i, valueWhere.asString().getValue());
				else if(valueWhere.isBoolean()) ps.setBoolean(i, valueWhere.asBoolean().getValue());
				else if(valueWhere.isDocument()) ps.setString(i, valueWhere.asDocument().toJson());
				else if(valueWhere.isDouble()) ps.setDouble(i, valueWhere.asDouble().getValue());
				else if(valueWhere.isInt32()) ps.setInt(i, valueWhere.asInt32().getValue());
				else if(valueWhere.isInt64()) ps.setLong(i, valueWhere.asInt64().getValue());
				else if(valueWhere.isNull()) ps.setNull(i, java.sql.Types.VARCHAR);
				else if(valueWhere.isTimestamp()) ps.setTimestamp(i, new Timestamp(valueWhere.asTimestamp().getValue()));
				else ps.setString(i, valueWhere.toString());
				ps.executeUpdate();
				ps.close();
				closeConnection(c);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return false;
		}
		
	}
	
	public static class FlatFile extends DataLoader {

		private File dataFolder;
		
		public FlatFile(File playerDataFolder) {
			super(DataType.FLATFILE);
			this.dataFolder = playerDataFolder;
		}

		@Override
		public void close() { }

		@Override
		public Set<JsonElement> getData(String key, BsonValue value) {
			Set<JsonElement> out = new HashSet<>();
			if(!key.equalsIgnoreCase("uuid") || !value.isString()) {
				// TODO parse through every json file.
				return null;
			}
			File f = new File(dataFolder, value.asString().getValue() + ".json");
			try {
				if(!f.exists()) f.createNewFile();
				FileReader fr = new FileReader(f);
				JsonReader jr = new JsonReader(fr);
				JsonParser parser = new JsonParser();
				out.add(parser.parse(jr));
				jr.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			return out;
		}

		@Override
		public boolean setData(String key, BsonValue value, List<BsonElement> elements) {
			if(!key.equalsIgnoreCase("uuid") || !value.isString()) {
				return false;
			}
			File f = new File(dataFolder, value.asString().getValue().concat(".json"));
			try {
				if(!f.exists()) f.createNewFile();
				FileReader fr = new FileReader(f);
				JsonReader jr = new JsonReader(fr);
				JsonParser parser = new JsonParser();
				JsonElement je = (parser.parse(jr));
				if(je.isJsonObject()) {
					JsonObject jo = je.getAsJsonObject();
					for(BsonElement el : elements) {
						BsonValue bv = el.getValue();
						Object val = "";
						if(bv.isString()) val = bv.asString().getValue();
						else if(bv.isBoolean()) val = bv.asBoolean().getValue() ? "true" : "false";
						else if(bv.isDocument()) val = bv.asDocument().toJson();
						else if(bv.isDouble()) val = bv.asDouble().getValue();
						else if(bv.isInt32()) val = bv.asInt32().getValue();
						else if(bv.isInt64()) val = bv.asInt64().getValue();
						else if(bv.isNull()) val = null;
						else if(bv.isDateTime()) val = bv.asDateTime().getValue();
						else val = bv.toString();
						jo.add(el.getName(), parser.parse(val.toString()));
					}
					FileWriter fw = new FileWriter(f);
					BufferedWriter bw = new BufferedWriter(fw);
					bw.write(jo.toString());
					bw.close();
				} else if(je.isJsonArray()) {
					jr.close();
					return false;
				} else if(je.isJsonNull()) {
					JsonObject jo = new JsonObject();
					for(BsonElement el : elements) {
						BsonValue bv = el.getValue();
						Object val = "";
						if(bv.isString()) val = bv.asString().getValue();
						else if(bv.isBoolean()) val = bv.asBoolean().getValue() ? "true" : "false";
						else if(bv.isDocument()) val = bv.asDocument().toJson();
						else if(bv.isDouble()) val = bv.asDouble().getValue();
						else if(bv.isInt32()) val = bv.asInt32().getValue();
						else if(bv.isInt64()) val = bv.asInt64().getValue();
						else if(bv.isNull()) val = null;
						else if(bv.isDateTime()) val = bv.asDateTime().getValue();
						else val = bv.toString();
						jo.add(el.getName(), parser.parse(val.toString()));
					}
					FileWriter fw = new FileWriter(f);
					BufferedWriter bw = new BufferedWriter(fw);
					bw.write(jo.toString());
					bw.close();
				} else {
					jr.close();
					return false;
				}
				jr.close();
			} catch (IOException e) {
				return false;
			}
			return true;
		}
		
	}
	
}
