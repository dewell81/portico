/*
 *   Copyright 2018 The Portico Project
 *
 *   This file is part of portico.
 *
 *   portico is free software; you can redistribute it and/or modify
 *   it under the terms of the Common Developer and Distribution License (CDDL) 
 *   as published by Sun Microsystems. For more information see the LICENSE file.
 *   
 *   Use of this software is strictly AT YOUR OWN RISK!!!
 *   If something bad happens you do not have permission to come crying to me.
 *   (that goes for your lawyer as well)
 *
 */
package org.portico2.lrc;

import org.apache.logging.log4j.Logger;
import org.portico.lrc.compat.JConfigurationException;
import org.portico.lrc.compat.JConnectionFailed;
import org.portico.lrc.compat.JException;
import org.portico.lrc.compat.JRTIinternalError;
import org.portico.utils.messaging.PorticoMessage;
import org.portico2.common.PorticoConstants;
import org.portico2.common.configuration.ConnectionConfiguration;
import org.portico2.common.messaging.MessageContext;
import org.portico2.common.network.ConnectionFactory;
import org.portico2.common.network.IConnection;
import org.portico2.common.network.IMessageReceiver;

public class LrcConnection implements IMessageReceiver
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private LRC lrc;
	private ConnectionConfiguration configuration;
	private IConnection connection;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public LrcConnection( LRC lrc, ConnectionConfiguration configuration ) throws JConfigurationException
	{
		this.lrc = lrc;
		this.configuration = configuration;
		
		// Create the underlying connection and configure it
		this.connection = ConnectionFactory.createConnection( configuration.getType() );
		this.connection.configure( configuration, this );
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	public void connect()
	{
		// 1. Tell the connection to attach
		this.connection.connect();
		
		// 2. Check to see if there is an RTI out there
		if( connection.findRti() == false )
		{
			this.connection.disconnect();
			throw new JConnectionFailed( "Could not find RTI running anywhere" );
		}
	}
	
	public void disconnect()
	{
		// 1. Tell the connection to detatch
		this.connection.disconnect();
	}

	///////////////////////////////////////////////////////////////////////////////////////
	///  Message SENDING methods   ////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
	public void sendControlRequest( MessageContext context ) throws JRTIinternalError
	{
		context.getRequest().setTargetFederate( PorticoConstants.RTI_HANDLE );
		this.connection.sendControlRequest( context );
	}
	
	public void sendDataMessage( PorticoMessage message ) throws JException
	{
		this.connection.sendDataMessage( message );
	}


	///////////////////////////////////////////////////////////////////////////////////////
	///  Message RECEIVING methods   //////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////
	@Override
	public void receiveControlRequest( MessageContext context ) throws JRTIinternalError
	{
		PorticoMessage request = context.getRequest();

		// Discard messages from other federates. As an LRC, we only care about control
		// information that has been received from the RTI
		if( request.isFromRti() == false )
			return;

		// Dump the request into the queue and send back a generic success response
		lrc.getState().getQueue().offer( request );
		context.success();
	}
	
	@Override
	public void receiveDataMessage( PorticoMessage incoming ) throws JException
	{
		// Dump the message into the LRC queue for processing
		lrc.getState().getQueue().offer( incoming );
	}

	@Override
	public final boolean isReceivable( int targetFederate )
	{
		// Just filter out ones targeting the RTI for now. We can filter the rest later.
		return targetFederate != PorticoConstants.RTI_HANDLE;
	}
	
	@Override
	public Logger getLogger()
	{
		return lrc.getLogger();
	}

	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
