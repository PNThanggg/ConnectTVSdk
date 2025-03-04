package com.connect.discovery.provider;

import android.content.Context;
import android.util.Log;

import com.connect.core.Util;
import com.connect.discovery.DiscoveryFilter;
import com.connect.discovery.DiscoveryProvider;
import com.connect.discovery.DiscoveryProviderListener;
import com.connect.discovery.provider.ssdp.SSDPClient;
import com.connect.discovery.provider.ssdp.SSDPDevice;
import com.connect.discovery.provider.ssdp.SSDPPacket;
import com.connect.service.config.ServiceDescription;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

public class SSDPDiscoveryProvider implements DiscoveryProvider {
    Context context;

    boolean needToStartSearch = false;

    private final CopyOnWriteArrayList<DiscoveryProviderListener> serviceListeners;

    ConcurrentHashMap<String, ServiceDescription> foundServices = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, ServiceDescription> discoveredServices = new ConcurrentHashMap<>();

    List<DiscoveryFilter> serviceFilters;

    private SSDPClient ssdpClient;

    private Timer scanTimer;

    private final Pattern uuidReg;

    private Thread responseThread;
    private Thread notifyThread;

    boolean isRunning = false;

    private final Object lock = new Object();

    public SSDPDiscoveryProvider(Context context) {
        this.context = context;

        uuidReg = Pattern.compile("(?<=uuid:)(.+?)(?=(::)|$)");

        serviceListeners = new CopyOnWriteArrayList<DiscoveryProviderListener>();
        serviceFilters = new CopyOnWriteArrayList<DiscoveryFilter>();
    }

    private void openSocket() {
        if (ssdpClient != null && ssdpClient.isConnected())
            return;

        try {
            InetAddress source = Util.getIpAddress(context);
            if (source == null)
                return;

            ssdpClient = createSocket(source);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected SSDPClient createSocket(InetAddress source) throws IOException {
        return new SSDPClient(source);
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (isRunning)
                return;

            isRunning = true;

            openSocket();

            scanTimer = new Timer();
            scanTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    sendSearch();
                }
            }, 100, RESCAN_INTERVAL);

            responseThread = new Thread(mResponseHandler);
            notifyThread = new Thread(mRespNotifyHandler);

            responseThread.start();
            notifyThread.start();
        }
    }

    public void sendSearch() {
        List<String> killKeys = new ArrayList<String>();

        long killPoint = new Date().getTime() - TIMEOUT;

        for (String key : foundServices.keySet()) {
            ServiceDescription service = foundServices.get(key);
            if (service == null || service.getLastDetection() < killPoint) {
                killKeys.add(key);
            }
        }

        for (String key : killKeys) {
            final ServiceDescription service = foundServices.get(key);

            if (service != null) {
                notifyListenersOfLostService(service);
            }

            foundServices.remove(key);
        }

        rescan();
    }

    @Override
    public void stop() {
        synchronized (lock) {
            isRunning = false;

            if (scanTimer != null) {
                scanTimer.cancel();
                scanTimer = null;
            }

            if (responseThread != null) {
                responseThread.interrupt();
                responseThread = null;
            }

            if (notifyThread != null) {
                notifyThread.interrupt();
                notifyThread = null;
            }

            if (ssdpClient != null) {
                ssdpClient.close();
                ssdpClient = null;
            }
        }
    }

    @Override
    public void restart() {
        stop();
        start();
    }

    @Override
    public void reset() {
        stop();
        foundServices.clear();
        discoveredServices.clear();
    }

    @Override
    public void rescan() {
        for (DiscoveryFilter searchTarget : serviceFilters) {
            final String message = SSDPClient.getSSDPSearchMessage(searchTarget.getServiceFilter());

            Timer timer = new Timer();
            /* Send 3 times like WindowsMedia */
            for (int i = 0; i < 3; i++) {
                TimerTask task = new TimerTask() {

                    @Override
                    public void run() {
                        try {
                            if (ssdpClient != null)
                                ssdpClient.send(message);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };

                timer.schedule(task, i * 1000);
            }
        }

    }

    @Override
    public void addDeviceFilter(DiscoveryFilter filter) {
        if (filter.getServiceFilter() == null) {
            Log.e(Util.T, "This device filter does not have ssdp filter info");
        } else {
            serviceFilters.add(filter);
        }
    }

    @Override
    public void removeDeviceFilter(DiscoveryFilter filter) {
        serviceFilters.remove(filter);
    }

    @Override
    public void setFilters(List<DiscoveryFilter> filters) {
        serviceFilters = filters;
    }

    @Override
    public boolean isEmpty() {
        return serviceFilters.isEmpty();
    }

    private final Runnable mResponseHandler = new Runnable() {
        @Override
        public void run() {
            while (ssdpClient != null) {
                try {
                    handleSSDPPacket(new SSDPPacket(ssdpClient.responseReceive()));
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    };

    private final Runnable mRespNotifyHandler = new Runnable() {
        @Override
        public void run() {
            while (ssdpClient != null) {
                try {
                    handleSSDPPacket(new SSDPPacket(ssdpClient.multicastReceive()));
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    };

    private void handleSSDPPacket(SSDPPacket ssdpPacket) {
        // Debugging stuff
//        Util.runOnUI(new Runnable() {
//
//            @Override
//            public void run() {
//                Log.d("Connect SDK Socket", "Packet received | type = " + ssdpPacket.type);
//
//                for (String key : ssdpPacket.data.keySet()) {
//                    Log.d("Connect SDK Socket", "    " + key + " = " + ssdpPacket.data.get(key));
//                }
//                Log.d("Connect SDK Socket", "__________________________________________");
//            }
//        });
        // End Debugging stuff

        if (ssdpPacket == null || ssdpPacket.getData().isEmpty() || ssdpPacket.getType() == null)
            return;

        String serviceFilter = ssdpPacket.getData().get(ssdpPacket.getType().equals(SSDPClient.NOTIFY) ? "NT" : "ST");

        if (serviceFilter == null || SSDPClient.MSEARCH.equals(ssdpPacket.getType()) || !isSearchingForFilter(serviceFilter))
            return;

        String usnKey = ssdpPacket.getData().get("USN");

        if (usnKey == null || usnKey.isEmpty())
            return;

        Matcher m = uuidReg.matcher(usnKey);

        if (!m.find())
            return;

        String uuid = m.group();

        if (SSDPClient.BYEBYE.equals(ssdpPacket.getData().get("NTS"))) {
            final ServiceDescription service = foundServices.get(uuid);

            if (service != null) {
                foundServices.remove(uuid);

                notifyListenersOfLostService(service);
            }
        } else {
            String location = ssdpPacket.getData().get("LOCATION");

            if (location == null || location.isEmpty())
                return;

            ServiceDescription foundService = foundServices.get(uuid);
            ServiceDescription discoverdService = discoveredServices.get(uuid);

            boolean isNew = foundService == null && discoverdService == null;

            if (isNew) {
                foundService = new ServiceDescription();
                foundService.setUUID(uuid);
                foundService.setServiceFilter(serviceFilter);
                foundService.setIpAddress(ssdpPacket.getDatagramPacket().getAddress().getHostAddress());
                foundService.setPort(3001);

                discoveredServices.put(uuid, foundService);

                getLocationData(location, uuid, serviceFilter);
            }

            if (foundService != null)
                foundService.setLastDetection(new Date().getTime());
        }
    }

    public void getLocationData(final String location, final String uuid, final String serviceFilter) {
        try {
            getLocationData(new URL(location), uuid, serviceFilter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getLocationData(final URL location, final String uuid, final String serviceFilter) {
        Util.runInBackground(() -> {
            SSDPDevice device = null;
            try {
                device = new SSDPDevice(location, serviceFilter);
            } catch (IOException | ParserConfigurationException | SAXException e) {
                e.printStackTrace();
            }

            if (device != null) {
                device.UUID = uuid;
                boolean hasServices = containsServicesWithFilter(device, serviceFilter);

                if (hasServices) {
                    final ServiceDescription service = discoveredServices.get(uuid);

                    if (service != null) {
                        service.setServiceFilter(serviceFilter);
                        service.setFriendlyName(device.friendlyName);
                        service.setModelName(device.modelName);
                        service.setModelNumber(device.modelNumber);
                        service.setModelDescription(device.modelDescription);
                        service.setManufacturer(device.manufacturer);
                        service.setApplicationURL(device.applicationURL);
                        service.setServiceList(device.serviceList);
                        service.setResponseHeaders(device.headers);
                        service.setLocationXML(device.locationXML);
                        service.setServiceURI(device.serviceURI);
                        service.setPort(device.port);

                        foundServices.put(uuid, service);

                        notifyListenersOfNewService(service);
                    }
                }
            }

            discoveredServices.remove(uuid);
        }, true);

    }

    private void notifyListenersOfNewService(ServiceDescription service) {
        List<String> serviceIds = serviceIdsForFilter(service.getServiceFilter());

        for (String serviceId : serviceIds) {
            ServiceDescription _newService = service.clone();
            _newService.setServiceID(serviceId);

            final ServiceDescription newService = _newService;

            Util.runOnUI(new Runnable() {

                @Override
                public void run() {

                    for (DiscoveryProviderListener listener : serviceListeners) {
                        listener.onServiceAdded(SSDPDiscoveryProvider.this, newService);
                    }
                }
            });
        }
    }

    private void notifyListenersOfLostService(ServiceDescription service) {
        List<String> serviceIds = serviceIdsForFilter(service.getServiceFilter());

        for (String serviceId : serviceIds) {
            ServiceDescription _newService = service.clone();
            _newService.setServiceID(serviceId);

            final ServiceDescription newService = _newService;

            Util.runOnUI(() -> {
                for (DiscoveryProviderListener listener : serviceListeners) {
                    listener.onServiceRemoved(SSDPDiscoveryProvider.this, newService);
                }
            });
        }
    }

    public List<String> serviceIdsForFilter(String filter) {
        ArrayList<String> serviceIds = new ArrayList<String>();

        for (DiscoveryFilter serviceFilter : serviceFilters) {
            String ssdpFilter = serviceFilter.getServiceFilter();

            if (ssdpFilter.equals(filter)) {
                String serviceId = serviceFilter.getServiceId();

                if (serviceId != null)
                    serviceIds.add(serviceId);
            }
        }

        return serviceIds;
    }

    public boolean isSearchingForFilter(String filter) {
        for (DiscoveryFilter serviceFilter : serviceFilters) {
            String ssdpFilter = serviceFilter.getServiceFilter();

            if (ssdpFilter.equals(filter))
                return true;
        }

        return false;
    }

    public boolean containsServicesWithFilter(SSDPDevice device, String filter) {
//        List<String> servicesRequired = new ArrayList<String>();
//
//        for (JSONObject serviceFilter : serviceFilters) {
//        }

    //  TODO  Implement this method.  Not sure why needs to happen since there are now required services.

        return true;
    }

    @Override
    public void addListener(DiscoveryProviderListener listener) {
        serviceListeners.add(listener);
    }

    @Override
    public void removeListener(DiscoveryProviderListener listener) {
        serviceListeners.remove(listener);
    }
}
