/*

ST:urn:schemas-upnp-org:device:MediaRenderer:1
USN:uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd::urn:schemas-upnp-org:device:MediaRenderer:1
Location:http://192.168.178.27:2869/upnphost/udhisapi.dll?content=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd
OPT:"http://schemas.upnp.org/upnp/1/0/"; ns=01
01-NLS:56fdffeaa746551bfd12ea5ba8cea45e
Cache-Control:max-age=1800
Server:Microsoft-Windows-NT/5.1 UPnP/1.0 UPnP-Device-Host/1.0
Ext:

<service>
<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
<serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>


<controlURL>/AVTransport/21fc4817-b8f7-ee43-1461-68a55e55fce0/control.xml</controlURL>


1.
curl -H ‘Content-Type: text/xml; charset=utf-8′ -H ‘SOAPAction: “urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI”‘ -d ‘<?xml version=”1.0″ encoding=”utf-8″?><s:Envelope s:encodingStyle=”http://schemas.xmlsoap.org/soap/encoding/” xmlns:s=”http://schemas.xmlsoap.org/soap/envelope/”><s:Body><u:SetAVTransportURI xmlns:u=”urn:schemas-upnp-org:service:AVTransport:1″><InstanceID>0</InstanceID><CurrentURI><![CDATA[http://my.site.com/path/to/my/content.mp4]]></CurrentURI><CurrentURIMetaData></CurrentURIMetaData></u:SetAVTransportURI></s:Body></s:Envelope>’ ‘http://192.168.1.101:59772/AVTransport/21fc4817-b8f7-ee43-1461-68a55e55fce0/control.xml‘
2.
curl -H ‘Content-Type: text/xml; charset=utf-8′ -H ‘SOAPAction: “urn:schemas-upnp-org:service:AVTransport:1#Play”‘ -d ‘<?xml version=”1.0″ encoding=”utf-8″?><s:Envelope s:encodingStyle=”http://schemas.xmlsoap.org/soap/encoding/” xmlns:s=”http://schemas.xmlsoap.org/soap/envelope/”><s:Body><u:Play xmlns:u=”urn:schemas-upnp-org:service:AVTransport:1″><InstanceID>0</InstanceID><Speed>1</Speed></u:Play></s:Body></s:Envelope>’ ‘http://192.168.1.101:59772/AVTransport/21fc4817-b8f7-ee43-1461-68a55e55fce0/control.xml‘




<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:microsoft="urn:schemas-microsoft-com:WMPDMR-1-0">
    <specVersion>
    <major>1</major>
    <minor>0</minor>
    </specVersion>
    <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>Jeroen (JWTC : Windows Media Player)</friendlyName>
    <modelNumber>12</modelNumber>
    <modelName>Windows Media Player</modelName>
    <modelDescription>Windows Media Player Renderer</modelDescription>
    <manufacturer>Microsoft Corporation</manufacturer>
    <manufacturerURL>http://www.microsoft.com</manufacturerURL>
    <modelURL>http://go.microsoft.com/fwlink/?LinkId=105927</modelURL>
    <serialNumber>{AB79375A-82A9-42D8-A496-C98DA451AE69}</serialNumber>
    <UDN>uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd</UDN>
    <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMR-1.50</dlna:X_DLNADOC>
    <microsoft:magicPacketSendSupported>1</microsoft:magicPacketSendSupported>
    <iconList>
    <icon>
    <mimetype>image/png</mimetype>
    <width>48</width>
    <height>48</height>
    <depth>24</depth>
    <url>/upnphost/udhisapi.dll?content=uuid:ad5398dd-7669-45da-a017-4a6f0fcf2df6</url>
    </icon>
    <icon>
    <mimetype>image/png</mimetype>
    <width>120</width>
    <height>120</height>
    <depth>24</depth>
    <url>/upnphost/udhisapi.dll?content=uuid:d8870a4c-7c2e-42d7-bd58-44d5d94120cd</url>
    </icon>
    <icon>
    <mimetype>image/jpeg</mimetype>
    <width>48</width>
    <height>48</height>
    <depth>24</depth>
    <url>/upnphost/udhisapi.dll?content=uuid:6dd0afdf-187f-4213-b9be-dbbe9d97e9ac</url>
    </icon>
    <icon>
    <mimetype>image/jpeg</mimetype>
    <width>120</width>
    <height>120</height>
    <depth>24</depth>
    <url>/upnphost/udhisapi.dll?content=uuid:15c2fa3e-7c2e-446c-9c6c-5447949855e6</url>
    </icon>
    </iconList>
    <serviceList>
    <service>
    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
    <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
    <controlURL>/upnphost/udhisapi.dll?control=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd+urn:upnp-org:serviceId:RenderingControl</controlURL>
    <eventSubURL>/upnphost/udhisapi.dll?event=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd+urn:upnp-org:serviceId:RenderingControl</eventSubURL>
    <SCPDURL>/upnphost/udhisapi.dll?content=uuid:dd1dcd47-ec9c-477a-9e03-7ef9ce11c5e5</SCPDURL>
    </service>
    <service>
    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
    <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
    <controlURL>/upnphost/udhisapi.dll?control=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd+urn:upnp-org:serviceId:AVTransport</controlURL>
    <eventSubURL>/upnphost/udhisapi.dll?event=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd+urn:upnp-org:serviceId:AVTransport</eventSubURL>
    <SCPDURL>/upnphost/udhisapi.dll?content=uuid:3b2a68f1-edd3-497b-b5bd-28197a66af35</SCPDURL>
    </service>
    <service>
    <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
    <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
    <controlURL>/upnphost/udhisapi.dll?control=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd+urn:upnp-org:serviceId:ConnectionManager</controlURL>
    <eventSubURL>/upnphost/udhisapi.dll?event=uuid:d7a34e60-99ad-4a2d-a3cd-295e354cf7cd+urn:upnp-org:serviceId:ConnectionManager</eventSubURL>
    <SCPDURL>/upnphost/udhisapi.dll?content=uuid:cacad500-7185-49de-b921-7f3aca9adc0a</SCPDURL>
    </service>
    </serviceList>
    </device>
    </root>
 */