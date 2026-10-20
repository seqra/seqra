import helpers.utils
import ldap3

server = ldap3.Server.from_definition('Mock LDAP Server', f'{helpers.utils.RES_DIR}/ldap_info.json', f'{helpers.utils.RES_DIR}/ldap_schema.json')
conn = ldap3.Connection(server, user='uid=admin,ou=system', password='secret', client_strategy = ldap3.MOCK_SYNC)
for (name, pw, addr) in [('foo', 'MrFooPa$$word', 'AddressForFoo #345'), ('MS Bar', 'barM$B4dPass', 'The streetz 4 Ms bar'), ('Mr Unknown', 'YouwontGue$$', 'Whe home is #678')]:
	attrs = {
		'uid': name,
		'cn': name,
		'street': addr,
		'sn': name,
		'userpassword': pw,
		'objectclass': ['top', 'person', 'organizationalPerson', 'inetorgperson']
	}
	conn.strategy.add_entry(f'uid={name},ou=users,ou=system', attrs)
conn.unbind()

def get_connection():
	conn = ldap3.Connection(server, user='uid=admin,ou=system', password='secret', client_strategy = ldap3.MOCK_SYNC)
	conn.bind()
	return conn
