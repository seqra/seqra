'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/xpathi-00/BenchmarkTest00024', methods=['GET'])
	def BenchmarkTest00024_get():
		response = make_response(render_template('web/xpathi-00/BenchmarkTest00024.html'))
		response.set_cookie('BenchmarkTest00024', '2222',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00024_post()

	@app.route('/benchmark/xpathi-00/BenchmarkTest00024', methods=['POST'])
	def BenchmarkTest00024_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00024", "noCookieValueSupplied"))

		import configparser
		
		bar = 'safe!'
		conf57764 = configparser.ConfigParser()
		conf57764.add_section('section57764')
		conf57764.set('section57764', 'keyA-57764', 'a_Value')
		conf57764.set('section57764', 'keyB-57764', param)
		bar = conf57764.get('section57764', 'keyA-57764')

		import lxml.etree
		import helpers.utils
		import io

		try:
			fd = open(f'{helpers.utils.RES_DIR}/employees.xml', 'rb')
			root = lxml.etree.parse(fd)
			strIO = io.StringIO()
			strIO.write('/Employees/Employee[@emplid=\'')
			strIO.write(bar)
			strIO.write('\']')
			query = strIO.getvalue()

			nodes = root.xpath(query)
			node_strings = []
			for node in nodes:
				node_strings.append(' '.join([e.text for e in node]))

			RESPONSE += (
				f'Your XPATH query results are: <br>[ {', '.join(node_strings)} ]'
			)
		except:
			RESPONSE += (
				f'Error parsing XPath Query: \'{escape_for_html(query)}\''
			)

		return RESPONSE

