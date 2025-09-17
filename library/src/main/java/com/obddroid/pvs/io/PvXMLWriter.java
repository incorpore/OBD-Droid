package com.obddroid.pvs.io;


import com.obddroid.pvs.ProcessVar;
import com.obddroid.pvs.PvChangeEvent;
import com.obddroid.pvs.PvChangeListener;

import org.xml.sax.helpers.AttributesImpl;

import java.io.OutputStream;
import java.util.Iterator;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * XML writer to write Process Variables into an XML-Stream
 *
 
 */
public class PvXMLWriter
	implements PvChangeListener
{

	private Result xFormResult;
	private SAXTransformerFactory factory;
	private TransformerHandler handler;
	private Transformer serializer;
	private boolean documentStarted = false;
	/** XML Tag-Names */
	public static final String TAG_PVLIST = "PV-LIST";
	public static final String TAG_PROCESSVAR = "PV";
	public static final String TAG_PVATTRIBUTE = "AT";
	/** XML attribute values */
	public static final String ATTR_TYPE = "type";
	public static final String ATTR_KEY = "key";
	public static final String ATTR_NAME = "name";

	/**
	 * Creates a new instance of PvXMLWriter
	 */
	private PvXMLWriter()
	{
		try
		{
			factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
			handler = factory.newTransformerHandler();
			serializer = handler.getTransformer();
			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT, "yes");
			serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new instance of PvXMLWriter
	 */
	public PvXMLWriter(OutputStream oStream)
	{
		this();
		setOutputStream(oStream);
	}

	/**
	 * set a stream writer for the handler
	 */
	private void setOutputStream(OutputStream oStream)
	{
		try
		{
			xFormResult = new StreamResult(oStream);
			handler.setResult(xFormResult);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * initialize document writing
	 */
	private void startDocument()
	{
		AttributesImpl attrs = new AttributesImpl();
		try
		{
			handler.startDocument();
			handler.startElement("", TAG_PVLIST, TAG_PVLIST, attrs);
			documentStarted = true;
		} catch (Throwable e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * finalize Document writing
	 */
	private void endDocument()
	{
		try
		{
			handler.endElement("", TAG_PVLIST, TAG_PVLIST);
			handler.endDocument();
			documentStarted = false;
		} catch (Throwable e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * write a process variable as XML to an output stream
	 *
	 * @param pv             processVar to write
	 * @param recursiveDepth Depth of recursive PV writing
	 * @param completeDoc    if TRUE, XML document will be initialized before,
	 *                       and finalized after writing this PV
	 */
	@SuppressWarnings("rawtypes")
	private void writePv(ProcessVar pv, int recursiveDepth, boolean completeDoc)
	{
		Object currKey;
		Object currVal;

		// Initialize XML-Document
		if (completeDoc || !documentStarted)
		{
			startDocument();
		}

		try
		{
			AttributesImpl attrs = new AttributesImpl();
			attrs.addAttribute("", ATTR_TYPE, ATTR_TYPE, "CDATA", pv.getClass().getName());
			attrs.addAttribute("", ATTR_KEY, ATTR_KEY, "CDATA", String.valueOf(pv.getKeyValue()));
			handler.startElement("", TAG_PROCESSVAR, TAG_PROCESSVAR, attrs);

			if (recursiveDepth > 0)// Loop through all PV-Attributes
			{
				Iterator it = pv.keySet().iterator();
				while (it.hasNext())
				{
					currKey = it.next();
					currVal = pv.get(currKey);

					attrs.clear();
					attrs.addAttribute("", ATTR_NAME, ATTR_NAME, "CDADTA", currKey.toString());
					handler.startElement("", TAG_PVATTRIBUTE, TAG_PVATTRIBUTE, attrs);

					if (currVal != null)
					{
						// If current value is a PV itself ...
						if (currVal instanceof ProcessVar)
						{
							// write recursively
							writePv((ProcessVar) currVal, recursiveDepth - 1, false);
						} else
						{
							// Otherwise write PV-Data as attribute
							String currStr = currVal.toString();
							handler.characters(currStr.toCharArray(), 0, currStr.length());
						}
					}
					// finish attribute XML-element
					handler.endElement("", TAG_PVATTRIBUTE, TAG_PVATTRIBUTE);
				}
			}
			// finish PV XNL-element
			handler.endElement("", TAG_PROCESSVAR, TAG_PROCESSVAR);
		} catch (Throwable e)
		{
			e.printStackTrace();
		}

		// Finalize XML document
		if (completeDoc)
		{
			endDocument();
		}
	}

	/**
	 * write a process variable as XML to an output stream
	 *
	 * @param pv             processVar to write
	 * @param recursiveDepth Depth of recursive PV writing
	 */
	public void writePv(ProcessVar pv, int recursiveDepth)
	{
		writePv(pv, recursiveDepth, false);
	}

	/**
	 * Standard writing of PV
	 * (uses recursive depth=100, NON complete Doc
	 *
	 * @param pv processVar to write
	 */
	private void writePv(ProcessVar pv)
	{
		writePv(pv, 100, false);
	}

	/**
	 * Implementation of PvChangeListener
	 */
	public void pvChanged(PvChangeEvent event)
	{
		// Test für XML-Ausgabe
		if (event.getValue() instanceof ProcessVar)
		{
			writePv((ProcessVar) event.getValue());
		}
	}
}
