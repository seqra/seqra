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

	@app.route('/benchmark/xpathi-00/BenchmarkTest00104', methods=['GET'])
	def BenchmarkTest00104_get():
		return BenchmarkTest00104_post()

	@app.route('/benchmark/xpathi-00/BenchmarkTest00104', methods=['POST'])
	def BenchmarkTest00104_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00104")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf82813 = configparser.ConfigParser()
		conf82813.add_section('section82813')
		conf82813.set('section82813', 'keyA-82813', 'a-Value')
		conf82813.set('section82813', 'keyB-82813', param)
		bar = conf82813.get('section82813', 'keyB-82813')

		import lxml.etree
		import helpers.utils

		try:
			fd = open(f'{helpers.utils.RES_DIR}/employees.xml', 'rb')
			root = lxml.etree.parse(fd)
			query = f'/Employees/Employee[@emplid=\'{bar}\']'
			run_query = lxml.etree.XPath(query)
			nodes = run_query(root)
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

