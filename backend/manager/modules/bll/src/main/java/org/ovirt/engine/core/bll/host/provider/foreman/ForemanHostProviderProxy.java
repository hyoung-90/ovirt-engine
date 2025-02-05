package org.ovirt.engine.core.bll.host.provider.foreman;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.core.bll.host.provider.ContentHostProvider;
import org.ovirt.engine.core.bll.host.provider.HostProviderProxy;
import org.ovirt.engine.core.bll.provider.BaseProviderProxy;
import org.ovirt.engine.core.common.businessentities.ErrataData;
import org.ovirt.engine.core.common.businessentities.Erratum;
import org.ovirt.engine.core.common.businessentities.ExternalComputeResource;
import org.ovirt.engine.core.common.businessentities.ExternalDiscoveredHost;
import org.ovirt.engine.core.common.businessentities.ExternalHostGroup;
import org.ovirt.engine.core.common.businessentities.Provider;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.errors.EngineError;
import org.ovirt.engine.core.common.errors.EngineException;
import org.ovirt.engine.core.common.queries.ErrataFilter;
import org.ovirt.engine.core.compat.Version;
import org.ovirt.engine.core.uutils.crypto.CryptMD5;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ForemanHostProviderProxy extends BaseProviderProxy implements HostProviderProxy {

    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String API_ENTRY_POINT = "/api/v2";
    private static final String JSON_FORMAT = "format=json";
    private static final String API_VERSION_ENTRY_POINT = API_ENTRY_POINT + "/status";
    private static final String HOSTS_ENTRY_POINT = API_ENTRY_POINT + "/hosts";
    private static final String LARGE_PAGE_SIZE = "&per_page=%s";

    private static final String ALL_HOSTS_QUERY = HOSTS_ENTRY_POINT + "?" + JSON_FORMAT;
    private static final String SEARCH_SECTION_FORMAT = "search=%1$s";
    static final String SEARCH_QUERY_FORMAT = "?" + SEARCH_SECTION_FORMAT + "&" + JSON_FORMAT;
    private static final String HOST_GROUPS_ENTRY_POINT = API_ENTRY_POINT + "/hostgroups";

    private static final String HOST_GROUPS_QUERY = HOST_GROUPS_ENTRY_POINT + "?" + JSON_FORMAT;
    private static final String COMPUTE_RESOURCES_HOSTS_ENTRY_POINT = API_ENTRY_POINT
            + "/compute_resources?search=" + URLEncoder.encode("oVirt|RHEV");

    private static final String DISCOVERED_HOSTS = "/discovered_hosts";

    private static final String DISCOVERED_HOSTS_ENTRY_POINT = API_ENTRY_POINT + DISCOVERED_HOSTS;
    private static final String DISCOVERED_HOSTS_QUERY = DISCOVERED_HOSTS_ENTRY_POINT + "?" + JSON_FORMAT;
    private static final Version KATELLO_V3_VERSION = new Version("1.11");


    public ForemanHostProviderProxy(Provider<?> hostProvider) {
        super(hostProvider);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    byte[] runHttpGetMethod(String relativeUrl) {
        return runHttpMethod(
                HttpMethodType.GET,
                "application/json; charset=utf-8",
                null,
                createConnection(relativeUrl));
    }

    private List<VDS> runHostListMethod(String relativeUrl) {
        try {
            ForemanHostWrapper fhw = objectMapper.readValue(runHttpGetMethod(relativeUrl), ForemanHostWrapper.class);
            return mapHosts(Arrays.asList(fhw.getResults()));
        } catch (IOException e) {
            return null;
        }
    }

    private List<ExternalDiscoveredHost> runDiscoveredHostListMethod(String relativeUrl) {
        try {
            ForemanDiscoveredHostWrapper fdw =
                    objectMapper.readValue(runHttpGetMethod(relativeUrl), ForemanDiscoveredHostWrapper.class);
            return mapDiscoveredHosts(Arrays.asList(fdw.getResults()));
        } catch (IOException e) {
            return null;
        }
    }

    private List<ExternalHostGroup> runHostGroupListMethod(String relativeUrl) {
        try {
            ForemanHostGroupWrapper fhgw =
                    objectMapper.readValue(runHttpGetMethod(relativeUrl), ForemanHostGroupWrapper.class);
            return mapHostGroups(Arrays.asList(fhgw.getResults()));
        } catch (IOException e) {
            return null;
        }
    }

    private List<ExternalComputeResource> runComputeResourceMethod(String relativeUrl) {
        try {
            ForemanComputerResourceWrapper fcrw =
                    objectMapper.readValue(runHttpGetMethod(relativeUrl), ForemanComputerResourceWrapper.class);
            return mapComputeResource(Arrays.asList(fcrw.getResults()));
        } catch (IOException e) {
            return null;
        }
    }

    // Mapping
    private List<ExternalComputeResource> mapComputeResource(List<ForemanComputerResource> foremanCrs) {
        List<ExternalComputeResource> crs = new ArrayList<>(foremanCrs.size());
        for (ForemanComputerResource cr : foremanCrs) {
            ExternalComputeResource computeResource = new ExternalComputeResource();
            computeResource.setName(cr.getName());
            computeResource.setUrl(cr.getUrl());
            computeResource.setId(cr.getId());
            computeResource.setProvider(cr.getProvider());
            computeResource.setUser(cr.getUser());
            crs.add(computeResource);
        }
        return crs;
    }

    private List<ExternalDiscoveredHost> mapDiscoveredHosts(List<ForemanDiscoveredHost> foremanHosts) {
        List<ExternalDiscoveredHost> hosts = new ArrayList<>(foremanHosts.size());
        for (ForemanDiscoveredHost host : foremanHosts) {
            ExternalDiscoveredHost dhost = new ExternalDiscoveredHost();
            dhost.setName(host.getName());
            dhost.setIp(host.getIp());
            dhost.setMac(host.getMac());
            dhost.setLastReport(host.getLastReport());
            dhost.setSubnetName(host.getSubnetName());
            hosts.add(dhost);
        }
        return hosts;
    }

    private List<VDS> mapHosts(List<ForemanHost> foremanHosts) {
        List<VDS> hosts = new ArrayList<>(foremanHosts.size());
        for (ForemanHost foremanHost : foremanHosts) {
            VDS host = new VDS();
            host.setVdsName(foremanHost.getName());
            host.setHostName(foremanHost.getName());
            hosts.add(host);
        }
        return hosts;
    }

    private List<ExternalHostGroup> mapHostGroups(List<ForemanHostGroup> foremanHostGroups) {
        Map<Integer, ExternalHostGroup> hostGroups = new HashMap<>();
        for (ForemanHostGroup hostGroup : foremanHostGroups) {
            ExternalHostGroup hostgroup = new ExternalHostGroup();
            hostgroup.setHostgroupId(hostGroup.getId());
            hostgroup.setName(hostGroup.getName());
            hostgroup.setTitle(hostGroup.getTitle());
            hostgroup.setOperatingsystemId(hostGroup.getOperatingSystemId());
            hostgroup.setEnvironmentId(hostGroup.getEnvironmentId());
            hostgroup.setDomainId(hostGroup.getDomainId());
            hostgroup.setSubnetId(hostGroup.getSubnetId());
            hostgroup.setParameters(hostGroup.getParameters());
            hostgroup.setMediumId(hostGroup.getMediumId());
            hostgroup.setArchitectureId(hostGroup.getArchitectureId());
            hostgroup.setPtableId(hostGroup.getPtableId());
            hostgroup.setOperatingsystemName(hostGroup.getOperatingSystemName());
            hostgroup.setDomainName(hostGroup.getDomainName());
            hostgroup.setSubnetName(hostGroup.getSubnetName());
            hostgroup.setArchitectureName(hostGroup.getArchitectureName());
            hostgroup.setAncestry(hostGroup.getAncestry());
            hostgroup.setEnvironmentName(hostGroup.getEnvironmentName());
            hostgroup.setPtableName(hostGroup.getPtableName());
            hostgroup.setMediumName(hostGroup.getMediumName());
            hostgroup.setPuppetCaProxyId(hostGroup.getPuppetCaProxyId());
            hostgroup.setPuppetProxyId(hostGroup.getPuppetProxyId());
            hostGroups.put(hostGroup.getId(), hostgroup);
        }
        List<ExternalHostGroup> ret = new ArrayList<>(foremanHostGroups.size());
        for (ForemanHostGroup hostGroup : foremanHostGroups) {
            if (hostGroup.getAncestry() != null) {
                String[] ancestries = hostGroup.getAncestry().split("/");
                if (hostGroup.getMediumName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String medName = hg.getMediumName();
                        if (medName != null) {
                            Integer medId = hg.getMediumId();
                            hostGroups.get(hostGroup.getId()).setMediumName(medName);
                            hostGroups.get(hostGroup.getId()).setMediumId(medId);
                            break;
                        }
                    }
                }
                if (hostGroup.getEnvironmentName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String envName = hg.getEnvironmentName();
                        if (envName != null) {
                            Integer envId = hg.getEnvironmentId();
                            hostGroups.get(hostGroup.getId()).setEnvironmentName(envName);
                            hostGroups.get(hostGroup.getId()).setEnvironmentId(envId);
                            break;
                        }
                    }
                }
                if (hostGroup.getPtableName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String ptableName = hg.getPtableName();
                        if (ptableName != null) {
                            Integer ptableId = hg.getPtableId();
                            hostGroups.get(hostGroup.getId()).setPtableName(ptableName);
                            hostGroups.get(hostGroup.getId()).setPtableId(ptableId);
                            break;
                        }
                    }
                }
                if (hostGroup.getArchitectureName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String archName = hg.getArchitectureName();
                        if (archName != null) {
                            Integer archId = hg.getArchitectureId();
                            hostGroups.get(hostGroup.getId()).setArchitectureName(archName);
                            hostGroups.get(hostGroup.getId()).setArchitectureId(archId);
                            break;
                        }
                    }
                }
                if (hostGroup.getOperatingSystemName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String osName = hg.getOperatingsystemName();
                        if (osName != null) {
                            Integer osId = hg.getOperatingsystemId();
                            hostGroups.get(hostGroup.getId()).setOperatingsystemName(osName);
                            hostGroups.get(hostGroup.getId()).setOperatingsystemId(osId);
                            break;
                        }
                    }
                }
                if (hostGroup.getDomainName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String domainName = hg.getDomainName();
                        if (domainName != null) {
                            Integer domainId = hg.getDomainId();
                            hostGroups.get(hostGroup.getId()).setDomainName(domainName);
                            hostGroups.get(hostGroup.getId()).setDomainId(domainId);
                            break;
                        }
                    }
                }
                if (hostGroup.getSubnetName() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        String subnetName = hg.getSubnetName();
                        if (subnetName != null) {
                            Integer subnetId = hg.getSubnetId();
                            hostGroups.get(hostGroup.getId()).setSubnetName(subnetName);
                            hostGroups.get(hostGroup.getId()).setSubnetId(subnetId);
                            break;
                        }
                    }
                }
                if (hostGroup.getParameters() == null) {
                    for (int i = ancestries.length - 1; i >= 0; i--) {
                        ExternalHostGroup hg = hostGroups.get(Integer.parseInt(ancestries[i]));
                        Map<String, String> parameters = hg.getParameters();
                        if (parameters != null) {
                            hostGroups.get(hostGroup.getId()).setParameters(parameters);
                            break;
                        }
                    }
                }
            }
            ret.add(hostGroups.get(hostGroup.getId()));
        }
        return ret;
    }

    private String buildLimitedPageSizeRequestUrl(String baseUrl) {
        String PAGE_SIZE = String.valueOf(Config.<Integer> getValue(ConfigValues.ForemanResponsePageSize));
        return baseUrl + String.format(LARGE_PAGE_SIZE, PAGE_SIZE);
    }

    /**
     *  Retrieves list of hosts from Foreman responding to the request URL:
     *  /api/v2/hosts?format=json&per_page=9999
     *
     * @return a list of hosts
     */
    @Override
    public List<VDS> getAll() {
        return runHostListMethod(buildLimitedPageSizeRequestUrl(ALL_HOSTS_QUERY));
    }

    /**
     *  Retrieves list of filtered hosts from Foreman responding to the request URL:
     *  /api/v2/hosts?search=FILTER&format=json&per_page=9999
     *
     * @param filter The filter for hosts
     * @return a list of filtered hosts
     */
    @Override
    public List<VDS> getFiltered(String filter) {
        String baseUrl = HOSTS_ENTRY_POINT + String.format(SEARCH_QUERY_FORMAT, filter);
        return runHostListMethod(buildLimitedPageSizeRequestUrl(baseUrl));
    }

    /**
     *  Retrieves list of discovered hosts from Foreman responding to the request URL:
     *  /api/v2/discovered_hosts?format=json&per_page=9999
     *
     * @return a list of discovered hosts
     */
    @Override
    public List<ExternalDiscoveredHost> getDiscoveredHosts() {
        return runDiscoveredHostListMethod(buildLimitedPageSizeRequestUrl(DISCOVERED_HOSTS_QUERY));
    }

    /**
     *  Retrieves list of host groups from Foreman responding to the request URL:
     *  /api/v2/hostgroups?format=json&per_page=9999
     *
     * @return a list of host groups
     */
    @Override
    public List<ExternalHostGroup> getHostGroups() {
        return runHostGroupListMethod(buildLimitedPageSizeRequestUrl(HOST_GROUPS_QUERY));
    }

    /**
     *  Retrieves list of compute resource from Foreman responding to the request URL:
     *  /api/v2/compute_resources?search=oVirt%7CRHEV&per_page=9999
     *
     * @return a list of compute resources
     */
    @Override
    public List<ExternalComputeResource> getComputeResources() {
        return runComputeResourceMethod(buildLimitedPageSizeRequestUrl(COMPUTE_RESOURCES_HOSTS_ENTRY_POINT));
    }

    @Override
    public void provisionHost(VDS host,
            ExternalHostGroup hg,
            ExternalComputeResource computeResource,
            String mac,
            String discoverName,
            String rootPassword,
            String ip) {
        final String entityBody = "{\n" +
                "    \"discovered_host\": {\n" +
                "        \"name\": \"" + host.getName() + "\",\n" +
                "        \"hostgroup_id\": \"" + hg.getHostgroupId() + "\",\n" +
                replaceIfExists(hg.getEnvironmentId(), "        \"environment_id\": \"%d\",\n") +
                "        \"mac\": \"" + mac + "\",\n" +
                replaceIfExists(hg.getDomainId(), "        \"domain_id\": \"%d\",\n") +
                replaceIfExists(hg.getSubnetId(), "        \"subnet_id\": \"%d\",\n") +
                "        \"ip\": \"" + ip + "\",\n" +
                replaceIfExists(hg.getArchitectureId(), "        \"architecture_id\": \"%d\",\n") +
                replaceIfExists(hg.getOperatingsystemId(), "        \"operatingsystem_id\": \"%d\",\n") +
                replaceIfExists(hg.getMediumId(), "        \"medium_id\": \"%d\",\n") +
                replaceIfExists(hg.getPtableId(), "        \"ptable_id\": \"%d\",\n") +
                replaceIfExists(hg.getPuppetProxyId(), "        \"puppet_proxy_id\": \"%s\",\n") +
                replaceIfExists(hg.getPuppetCaProxyId(), "        \"puppet_ca_proxy_id\": \"%s\",\n") +
                "        \"root_pass\": \"" + rootPassword + "\",\n" +
                "        \"build\": \"" + Boolean.TRUE + "\",\n" +
                "        \"host_parameters_attributes\": [\n" +
                "           {\n" +
                "                \"name\": \"host_ovirt_id\",\n" +
                "                \"value\": \"" + host.getId() + "\",\n" +
                "                \"_destroy\": \"false\"\n" +
                "            },\n" +
                "           {\n" +
                "                \"name\": \"compute_resource_id\",\n" +
                "                \"value\": \"" + computeResource.getId() + "\",\n" +
                "                \"_destroy\": \"false\"\n" +
                "            },\n" +
                "           {\n" +
                "                \"name\": \"pass\",\n" +
                "                \"value\": \"" + CryptMD5.crypt(rootPassword) + "\",\n" +
                "                \"_destroy\": \"false\"\n" +
                "            },\n" +
                "           {\n" +
                "                \"name\": \"management\",\n" +
                "                \"value\": \"" + computeResource.getUrl().replaceAll("(http://|/api|/ovirt-engine)", "") + "\",\n" +
                "                \"_destroy\": \"false\"\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}";
        runHttpMethod(
                HttpMethodType.PUT,
                "application/json; charset=utf-8",
                entityBody,
                createConnection(DISCOVERED_HOSTS_ENTRY_POINT + "/" + discoverName)
                );
    }

    private <T> String replaceIfExists(T input, String replacement) {
        if (input == null) {
            return "";
        }

        return String.format(replacement, input);
    }

    @Override
    protected void afterReadResponse(HttpURLConnection connection, byte[] response) throws Exception {
        if (isUnsuccessfulResponseCode(connection)) {
            ForemanErrorWrapper ferr = objectMapper.readValue(response, ForemanErrorWrapper.class);
            String err = StringUtils.join(ferr.getForemanError().getFullMessages(), ", ");
            throw new EngineException(EngineError.PROVIDER_FAILURE, err);
        }
    }

    @Override
    public void testConnection() {
        runHttpGetMethod(API_ENTRY_POINT);

        // validate permissions to discovered host and host group.
        getDiscoveredHosts();
        getHostGroups();
    }

    @Override
    public ErrataData getErrataForHost(ContentHostIdentifier contentHostIdentifier, ErrataFilter errataFilter) {
        return getContentHostProvider().getErrataForHost(contentHostIdentifier, errataFilter);
    }

    @Override
    public Erratum getErratumForHost(ContentHostIdentifier contentHostIdentifier, String erratumId) {
        return getContentHostProvider().getErratumForHost(contentHostIdentifier, erratumId);
    }

    @Override
    public boolean isContentHostExist(ContentHostIdentifier contentHostIdentifier) {
        return getContentHostProvider().isContentHostExist(contentHostIdentifier);
    }

    private ContentHostProvider getContentHostProvider() {
        Version foremanVersion = getForemanVersion();
        if (foremanVersion != null && foremanVersion.greaterOrEquals(KATELLO_V3_VERSION)) {
            return new KatelloV30Provider(this);
        } else {
            return new KatelloV21Provider(this);
        }
    }

    private Version getForemanVersion() {
        try {
            ReportedForemanStatus status =
                    objectMapper.readValue(runHttpGetMethod(API_VERSION_ENTRY_POINT), ReportedForemanStatus.class);
            return new Version(status.getVersion());
        } catch (IOException e) {
            log.warn(
                    "Unable to detect Foreman version for provider {}. Using older version to connect to the provider",
                    getProvider().getName());
            return null;
        }
    }
}
