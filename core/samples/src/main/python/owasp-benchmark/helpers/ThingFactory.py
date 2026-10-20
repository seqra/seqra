import helpers.utils
import configparser

def createThing():
	config = configparser.ConfigParser()
	config.read(f'{helpers.utils.RES_DIR}/ThingFactory.ini')
	cname = config.get('DEFAULT', 'thing', fallback='Thing1')
	cobj = getattr(helpers.ThingFactory, cname)
	obj = cobj()
	return obj

class Thing1:
	def doSomething(self, x: str):
		ret = x
		return ret

class Thing2:
	def doSomething(self, x: str):
		if not x:
			return ''
		return '' + x
