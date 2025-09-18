package pvs;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Vector;
import java.util.logging.Level;

/**
 * Database backed process variable
 *

 */
public class DbProcessVar extends IndexedProcessVar
{

	/**
	 *
	 */
	private static final long serialVersionUID = 1080116313924821619L;
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final Vector<String> fields = new Vector();
	/**
	 * Holds value of property dbConnection.
	 */
	private Connection dbConnection;
	/**
	 * Holds value of property tableName.
	 */
	private String tableName;

	/** Creates a new instance of DbProcessVar */
	private DbProcessVar()
	{
	}

	public DbProcessVar(Connection dbConn)
	{
		this();
		setDbConnection(dbConn);
	}

	public DbProcessVar(Connection dbConn, String tableName)
	{
		this();
		setDbConnection(dbConn);
		setTableName(tableName);
	}

	/**
	 * return all available field names
	 */
	public String[] getFields()
	{
		return ((String[]) fields.toArray());
	}

	/**
	 * Getter for property dbConnection.
	 *
	 * @return Value of property dbConnection.
	 */
	public Connection getDbConnection()
	{

		return this.dbConnection;
	}

	/**
	 * Setter for property dbConnection.
	 *
	 * @param dbConnection New value of property dbConnection.
	 */
	private void setDbConnection(Connection dbConnection)
	{

		this.dbConnection = dbConnection;
	}

	/**
	 * Getter for property tableName.
	 *
	 * @return Value of property tableName.
	 */
	public String getTableName()
	{
		return this.tableName;
	}

	/**
	 * Setter for property tableName.
	 *
	 * @param tableName New value of property tableName.
	 */
	private void setTableName(String tableName)
	{

		this.tableName = tableName;
	}

	protected String getSelectSQL()
	{
		return ("SELECT * from " + tableName);
	}

	protected ResultSet getResultSet(String sqlQuery)
	{
		ResultSet set = null;
		ResultSetMetaData rsMd;
		try
		{
			set = dbConnection.createStatement().executeQuery(sqlQuery);
			rsMd = set.getMetaData();
			for (int i = 0; i < rsMd.getColumnCount(); i++)
			{
				fields.add(i, rsMd.getColumnName(i));
			}
		} catch (Exception e)
		{
			log.log(Level.SEVERE, e.toString(), e);
		}
		return (set);
	}
}
