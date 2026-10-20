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

	@app.route('/benchmark/xpathi-02/BenchmarkTest01032', methods=['GET'])
	def BenchmarkTest01032_get():
		return BenchmarkTest01032_post()

	@app.route('/benchmark/xpathi-02/BenchmarkTest01032', methods=['POST'])
	def BenchmarkTest01032_post():
		RESPONSE = ""

		parts = request.path.split("/")
		param = parts[1]
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf71116 = configparser.ConfigParser()
		conf71116.add_section('section71116')
		conf71116.set('section71116', 'keyA-71116', 'a_Value')
		conf71116.set('section71116', 'keyB-71116', param)
		bar = conf71116.get('section71116', 'keyA-71116')

		import lxml.etree
		import helpers.utils

		try:
			if '\'' in bar:
				RESPONSE += (
					"Employee ID must not contain apostrophes"
				)
				return RESPONSE

			fd = open(f'{helpers.utils.RES_DIR}/employees.xml', 'rb')
			root = lxml.etree.parse(fd)
			query = f'/Employees/Employee[@emplid=\'{bar}\']'
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

