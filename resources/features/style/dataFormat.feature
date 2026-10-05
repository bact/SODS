Feature: Data format

  Scenario: A date/time format survives setting a date value
    Given a sheet "A", size 1x1
    When set a DataFormat.dateTime with pattern "dd/MM/yyyy" to cell 0,0
    And set the date "2026-10-05" in cell 0,0
    Then the DataFormat of cell 0,0 has pattern "dd/MM/yyyy"

  Scenario Outline: An unsupported pattern is rejected with a fix
    When create a DataFormat with invalid "<kind>" pattern "<pattern>" and catch the exception
    Then the last exception message contains "<fix>"

    Examples:
      | kind     | pattern | fix  |
      | dateTime | hh:mm   | HH   |
      | number   | 0.0#    | 0.00 |
